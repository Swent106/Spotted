// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.thanks

import com.android.spotted.model.thanks.AlreadyThankedException
import com.android.spotted.model.thanks.Thanks
import com.android.spotted.model.thanks.ThanksRepository
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * [ThanksRepository] storing one document per (alert, helper) in `thanks/{alertId}_{helperId}`, so
 * a helper can't be thanked twice for the same alert. The count is a Firestore `count()` query:
 * there is no stored counter to keep in sync or to protect.
 */
class ThanksRepositoryFirestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : ThanksRepository {

  private val thanksCollection
    get() = firestore.collection(THANKS_COLLECTION)

  override suspend fun thank(thanks: Thanks): Result<Unit> {
    if (thanks.helperId == thanks.ownerId) {
      return Result.failure(IllegalArgumentException("A user can't thank themselves"))
    }
    return firestoreResult {
      val ref = thanksCollection.document(thanks.id)
      // Read and write in one transaction, so two quick taps store one thanks and the second one
      // gets AlreadyThankedException instead of a rules refusal
      firestore
          .runTransaction { transaction ->
            if (transaction.get(ref).exists()) {
              throw AlreadyThankedException(thanks.alertId, thanks.helperId)
            }
            transaction.set(ref, thanks.toMap())
          }
          .await()
    }
  }

  override suspend fun getThanksCount(helperId: String): Result<Int> = firestoreResult {
    thanksCollection
        .whereEqualTo(HELPER_ID, helperId)
        .count()
        .get(AggregateSource.SERVER)
        .await()
        .count
        .toInt()
  }

  // Only the latest list matters: a slow collector skips the lists it missed
  override fun observeThanksReceived(helperId: String): Flow<Result<List<Thanks>>> =
      thanksUpdates(helperId).conflate()

  private fun thanksUpdates(helperId: String): Flow<Result<List<Thanks>>> = callbackFlow {
    // Called with the current list, then after every change, off the main thread.
    // No limit in the query: every thanks of the helper is downloaded, then cut to
    // MAX_THANKS_RECEIVED below. Fine for the counts we expect; if it grows, use
    // orderBy(thankedAtMillis) + limit(MAX_THANKS_RECEIVED) with a composite index instead.
    val registration =
        thanksCollection.whereEqualTo(HELPER_ID, helperId).addSnapshotListener(
            Dispatchers.Default.asExecutor()
        ) { snapshots, error ->
          if (error != null) {
            trySend(Result.failure(error))
            close()
          } else if (snapshots != null) {
            // Sorted here rather than in the query, which would need a composite index
            val received =
                snapshots.documents
                    .mapNotNull { it.toThanks() }
                    .sortedWith(compareByDescending<Thanks> { it.thankedAtMillis }.thenBy { it.id })
                    .take(ThanksRepository.MAX_THANKS_RECEIVED)
            trySend(Result.success(received))
          }
        }
    awaitClose { registration.remove() }
  }

  /** Runs [block] on the IO dispatcher and returns its outcome as a [Result]; never throws. */
  private suspend fun <T> firestoreResult(block: suspend () -> T): Result<T> =
      try {
        Result.success(withContext(Dispatchers.IO) { block() })
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        // Firestore may wrap what the transaction throws: give back the refusal as it is
        val alreadyThanked =
            generateSequence<Throwable>(exception) { it.cause }
                .filterIsInstance<AlreadyThankedException>()
                .firstOrNull()
        Result.failure(alreadyThanked ?: exception)
      }

  private fun Thanks.toMap(): Map<String, Any> =
      mapOf(
          ALERT_ID to alertId,
          OWNER_ID to ownerId,
          HELPER_ID to helperId,
          SIGHTING_ID to sightingId,
          PET_NAME to petName,
          THANKED_AT_MILLIS to thankedAtMillis,
      )

  /**
   * The thanks in this document, or null if a field is missing or has the wrong type (it is then
   * left out). getString and getLong throw on a wrong type, which would otherwise crash the app.
   */
  private fun DocumentSnapshot.toThanks(): Thanks? =
      try {
        Thanks(
            alertId = getString(ALERT_ID) ?: return null,
            ownerId = getString(OWNER_ID) ?: return null,
            helperId = getString(HELPER_ID) ?: return null,
            sightingId = getString(SIGHTING_ID) ?: return null,
            petName = getString(PET_NAME) ?: return null,
            thankedAtMillis = getLong(THANKED_AT_MILLIS) ?: return null,
        )
      } catch (exception: RuntimeException) {
        null
      }

  private companion object {
    const val THANKS_COLLECTION = "thanks"
    const val ALERT_ID = "alertId"
    const val OWNER_ID = "ownerId"
    const val HELPER_ID = "helperId"
    const val SIGHTING_ID = "sightingId"
    const val PET_NAME = "petName"
    const val THANKED_AT_MILLIS = "thankedAtMillis"
  }
}
