// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.moderation

import com.android.spotted.model.moderation.BlockRepository
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

/**
 * [BlockRepository] storing one document per blocked user in `users/{uid}/blocks/{blockedUid}`, so
 * the Firestore rules can keep the list private to its owner.
 */
class BlockRepositoryFirestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : BlockRepository {

  private fun blocks(userId: String) =
      firestore.collection(USERS_COLLECTION).document(userId).collection(BLOCKS_COLLECTION)

  // Only the latest list matters: a slow collector skips the lists it missed
  override fun observeBlocked(userId: String): Flow<Result<Set<String>>> =
      blockedUpdates(userId).conflate()

  private fun blockedUpdates(userId: String): Flow<Result<Set<String>>> = callbackFlow {
    // Called with the current list, then after every change, off the main thread
    val registration =
        blocks(userId).addSnapshotListener(Dispatchers.Default.asExecutor()) { snapshots, error ->
          if (error != null) {
            trySend(Result.failure(error))
            close()
          } else if (snapshots != null) {
            trySend(Result.success(snapshots.documents.map { it.id }.toSet()))
          }
        }
    awaitClose { registration.remove() }
  }

  override suspend fun block(userId: String, blockedUserId: String): Result<Unit> {
    if (userId == blockedUserId) {
      return Result.failure(IllegalArgumentException("A user can't block themselves"))
    }
    return firestoreResult {
      Tasks.await(
          blocks(userId).document(blockedUserId).set(mapOf(BLOCKED_UID_FIELD to blockedUserId))
      )
    }
  }

  override suspend fun unblock(userId: String, blockedUserId: String): Result<Unit> =
      firestoreResult {
        Tasks.await(blocks(userId).document(blockedUserId).delete())
      }

  /** Runs [block] on the IO dispatcher and returns its outcome as a [Result]; never throws. */
  private suspend fun firestoreResult(block: () -> Any?): Result<Unit> =
      try {
        withContext(Dispatchers.IO) { block() }
        Result.success(Unit)
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: ExecutionException) {
        Result.failure(exception.cause ?: exception)
      } catch (exception: Exception) {
        Result.failure(exception)
      }

  private companion object {
    const val USERS_COLLECTION = "users"
    const val BLOCKS_COLLECTION = "blocks"
    const val BLOCKED_UID_FIELD = "blockedUid"
  }
}
