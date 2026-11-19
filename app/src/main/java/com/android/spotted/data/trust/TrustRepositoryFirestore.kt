// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.trust

import com.android.spotted.model.trust.SightingNotFoundException
import com.android.spotted.model.trust.TrustRepository
import com.android.spotted.model.trust.TrustScore
import com.android.spotted.model.trust.Verdict
import com.android.spotted.model.trust.VerdictAlreadyAppliedException
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [TrustRepository] storing `trust/{uid}` documents (`score`, `judged`, and `lastSightingId`, the
 * sighting whose verdict was applied last). A user without a document has no judged sighting yet
 * and reads as [TrustScore.INITIAL].
 *
 * A judged sighting gets a `trustVerdict` field, so it is counted once. The Firestore rules only
 * accept a trust write made together with that field, by the owner of the sighting's alert, and
 * with the score [TrustScore.after] gives.
 */
class TrustRepositoryFirestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : TrustRepository {
  private val trust = firestore.collection(TRUST_COLLECTION)
  private val sightings = firestore.collection(SIGHTINGS_COLLECTION)

  override suspend fun getTrust(uid: String): Result<TrustScore> = firestoreResult {
    Tasks.await(trust.document(uid).get()).toTrustScore()
  }

  /**
   * Two verdicts given at the same time both read the same trust, and the second to commit is
   * stale. Firestore checks the rules before it notices the conflict, so instead of retrying it the
   * rules refuse it (its judged count is no longer the stored one + 1) with PERMISSION_DENIED. The
   * transaction is then run again, from the trust the first verdict wrote. A write that is really
   * not allowed is refused every time, and that refusal is returned after the last attempt.
   */
  override suspend fun applyVerdict(sightingId: String, verdict: Verdict): Result<TrustScore> {
    var attempt = 1
    while (true) {
      val result = firestoreResult { runVerdictTransaction(sightingId, verdict) }
      val denied =
          (result.exceptionOrNull() as? FirebaseFirestoreException)?.code ==
              FirebaseFirestoreException.Code.PERMISSION_DENIED
      if (!denied || attempt >= MAX_VERDICT_ATTEMPTS) return result
      attempt++
    }
  }

  /**
   * Reads the sighting and its reporter's trust, and writes both back in one transaction. A
   * conflict seen by Firestore itself (e.g. on the sighting) is retried by the transaction.
   */
  private fun runVerdictTransaction(sightingId: String, verdict: Verdict): TrustScore {
    val sightingRef = sightings.document(sightingId)
    val transaction = firestore.runTransaction { transaction ->
      val sighting = transaction.get(sightingRef)
      if (!sighting.exists()) throw SightingNotFoundException(sightingId)
      // The first verdict is final: changing it would mean replaying the whole history
      if (sighting.getString(TRUST_VERDICT_FIELD) != null) {
        throw VerdictAlreadyAppliedException(sightingId)
      }
      val reporterId =
          checkNotNull(sighting.getString(REPORTER_ID_FIELD)) {
            "sightings/$sightingId has no reporterId"
          }
      val trustRef = trust.document(reporterId)
      val next = transaction.get(trustRef).toTrustScore().after(verdict)
      transaction.set(
          trustRef,
          mapOf(
              SCORE_FIELD to next.score,
              JUDGED_FIELD to next.judged,
              LAST_SIGHTING_ID_FIELD to sightingId,
          ),
      )
      transaction.update(sightingRef, TRUST_VERDICT_FIELD, verdict.name)
      next
    }
    return Tasks.await(transaction)
  }

  /** The stored trust, or [TrustScore.INITIAL] if there is no document. */
  private fun DocumentSnapshot.toTrustScore(): TrustScore {
    if (!exists()) return TrustScore.INITIAL
    val score = checkNotNull(getDouble(SCORE_FIELD)) { "trust/$id has no score" }
    val judged = checkNotNull(getLong(JUDGED_FIELD)) { "trust/$id has no judged" }
    return TrustScore(score, judged.toInt())
  }

  /**
   * Runs [action] on [Dispatchers.IO] and returns its result. Errors become a [Result.failure];
   * what the transaction threw (e.g. [VerdictAlreadyAppliedException]) is given back as it is, even
   * when Firestore wraps it.
   */
  private suspend fun <T> firestoreResult(action: () -> T): Result<T> =
      try {
        Result.success(withContext(Dispatchers.IO) { action() })
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        val cause = if (exception is ExecutionException) exception.cause ?: exception else exception
        val thrownInTransaction =
            generateSequence<Throwable>(cause) { it.cause }
                .firstOrNull {
                  it is VerdictAlreadyAppliedException || it is SightingNotFoundException
                }
        Result.failure(thrownInTransaction ?: cause)
      }

  private companion object {
    const val TRUST_COLLECTION = "trust"
    const val SIGHTINGS_COLLECTION = "sightings"
    const val SCORE_FIELD = "score"
    const val JUDGED_FIELD = "judged"
    const val LAST_SIGHTING_ID_FIELD = "lastSightingId"
    const val REPORTER_ID_FIELD = "reporterId"
    const val TRUST_VERDICT_FIELD = "trustVerdict"

    /** Enough for a few verdicts given at the same time on the same reporter. */
    const val MAX_VERDICT_ATTEMPTS = 5
  }
}
