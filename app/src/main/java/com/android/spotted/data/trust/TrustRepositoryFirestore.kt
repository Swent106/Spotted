// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.trust

import com.android.spotted.data.trust.TrustDocuments.PENALTY_FIELD
import com.android.spotted.data.trust.TrustDocuments.TRUST_COLLECTION
import com.android.spotted.data.trust.TrustDocuments.TRUST_VERDICT_FIELD
import com.android.spotted.data.trust.TrustDocuments.judge
import com.android.spotted.data.trust.TrustDocuments.reporterIdOf
import com.android.spotted.data.trust.TrustDocuments.setTrust
import com.android.spotted.data.trust.TrustDocuments.toTrustScore
import com.android.spotted.model.sighting.SightingStatus
import com.android.spotted.model.trust.SightingNotFoundException
import com.android.spotted.model.trust.SightingNotHiddenException
import com.android.spotted.model.trust.TrustRepository
import com.android.spotted.model.trust.TrustScore
import com.android.spotted.model.trust.Verdict
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Transaction
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [TrustRepository] storing `trust/{uid}` documents (`score`, `judged`, `falseSightings`,
 * `penaltiesApplied`, and `lastSightingId`, the sighting whose verdict changed last). A user
 * without a document has no judged sighting yet and reads as [TrustScore.INITIAL].
 *
 * A judged sighting gets a `trustVerdict` field, so it is counted once. The Firestore rules only
 * accept a trust write made together with that field and with the score [TrustScore.after] gives.
 * The owner of the sighting's alert may only confirm it ([confirmSighting]); a false verdict comes
 * from the votes (`VoteRepositoryFirestore`), and the owner may then cancel it by restoring the
 * sighting ([restoreSighting]). `trustVerdict` (what counted) is kept apart from `status` (what the
 * owner sees): the status may change later, the verdict cannot.
 */
class TrustRepositoryFirestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : TrustRepository {
  private val trust = firestore.collection(TRUST_COLLECTION)
  private val sightings = firestore.collection(SIGHTINGS_COLLECTION)

  override suspend fun getTrust(uid: String): Result<TrustScore> = firestoreResult {
    Tasks.await(trust.document(uid).get()).toTrustScore()
  }

  override suspend fun confirmSighting(sightingId: String): Result<Unit> = retryingStaleTrust {
    runSightingTransaction(sightingId) { transaction, sighting ->
      if (sighting.getString(TRUST_VERDICT_FIELD) != null) {
        // Already counted (e.g. confirmed, dismissed, now confirmed again): only the status moves
        transaction.update(sighting.reference, STATUS_FIELD, SightingStatus.CONFIRMED.name)
      } else {
        transaction.judge(
            firestore,
            sighting,
            Verdict.CONFIRMED,
            mapOf(STATUS_FIELD to SightingStatus.CONFIRMED.name),
        )
      }
      Unit
    }
  }

  override suspend fun restoreSighting(sightingId: String): Result<Unit> = retryingStaleTrust {
    runSightingTransaction(sightingId) { transaction, sighting ->
      val hidden = sighting.getString(STATUS_FIELD) == SightingStatus.VOTED_FALSE.name
      val beforePenalty = (sighting.get(PENALTY_FIELD) as? Map<*, *>)?.toTrustScore()
      if (
          !hidden || sighting.getBoolean(PENALTY_CANCELLED_FIELD) == true || beforePenalty == null
      ) {
        throw SightingNotHiddenException(sightingId)
      }
      val reporterId = reporterIdOf(sighting)
      val current = transaction.get(trust.document(reporterId)).toTrustScore()
      transaction.setTrust(firestore, reporterId, current.withoutPenalty(beforePenalty), sightingId)
      // trustVerdict stays: the sighting stays judged, so the votes can never hide it again
      transaction.update(
          sighting.reference,
          mapOf(
              STATUS_FIELD to SightingStatus.PENDING.name,
              PENALTY_CANCELLED_FIELD to true,
          ),
      )
      Unit
    }
  }

  /**
   * Runs [transaction] again while the rules refuse it, at most [MAX_VERDICT_ATTEMPTS] times.
   *
   * Two verdicts given at the same time both read the same trust, and the second to commit is
   * stale. Firestore checks the rules before it notices the conflict, so instead of retrying it the
   * rules refuse it (its judged count is no longer the stored one + 1) with PERMISSION_DENIED. The
   * transaction is then run again, from the trust the first verdict wrote. A write that is really
   * not allowed is refused every time, and that refusal is returned after the last attempt.
   *
   * The two cannot be told apart from the error, so a real refusal (e.g. by a user who does not own
   * the alert) is also tried [MAX_VERDICT_ATTEMPTS] times. That only costs a few round trips before
   * the error is shown, and changes nothing: every attempt is refused.
   */
  private suspend fun <T> retryingStaleTrust(transaction: () -> T): Result<T> {
    var attempt = 1
    while (true) {
      val result = firestoreResult(transaction)
      val denied =
          (result.exceptionOrNull() as? FirebaseFirestoreException)?.code ==
              FirebaseFirestoreException.Code.PERMISSION_DENIED
      if (!denied || attempt >= MAX_VERDICT_ATTEMPTS) return result
      attempt++
    }
  }

  /**
   * Reads the sighting [sightingId] in a transaction and gives it to [body], which may read more
   * and then write. Nothing is written if [body] throws. A conflict seen by Firestore itself (e.g.
   * on the sighting) is retried by the transaction.
   */
  private fun <T> runSightingTransaction(
      sightingId: String,
      body: (Transaction, DocumentSnapshot) -> T,
  ): T {
    val sightingRef = sightings.document(sightingId)
    val transaction = firestore.runTransaction { transaction ->
      val sighting = transaction.get(sightingRef)
      if (!sighting.exists()) throw SightingNotFoundException(sightingId)
      body(transaction, sighting)
    }
    return Tasks.await(transaction)
  }

  /**
   * Runs [action] on [Dispatchers.IO] and returns its result. Errors become a [Result.failure];
   * what the transaction threw ([SightingNotFoundException], [SightingNotHiddenException]) is given
   * back as it is, even when Firestore wraps it.
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
                .firstOrNull { it is SightingNotFoundException || it is SightingNotHiddenException }
        Result.failure(thrownInTransaction ?: cause)
      }

  private companion object {
    const val SIGHTINGS_COLLECTION = "sightings"
    const val STATUS_FIELD = "status"

    /** Set on a restored sighting: its penalty was cancelled. */
    const val PENALTY_CANCELLED_FIELD = "penaltyCancelled"

    /** Enough for a few verdicts given at the same time on the same reporter. */
    const val MAX_VERDICT_ATTEMPTS = 5
  }
}
