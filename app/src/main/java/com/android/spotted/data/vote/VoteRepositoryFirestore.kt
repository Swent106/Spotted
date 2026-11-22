// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.vote

import com.android.spotted.model.vote.AlreadyVotedException
import com.android.spotted.model.vote.VoteRepository
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [VoteRepository] storing one document per voter in `sightings/{sightingId}/votes/{voterId}`, with
 * the fields `voterId` and `votedAt` (server time, so the rules can limit votes per day).
 *
 * The document id is the voter's uid, so a user has at most one vote per sighting. The rules for
 * this subcollection (who may vote, and that a vote is never changed) come with #462: until then
 * Firestore refuses every call.
 */
class VoteRepositoryFirestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : VoteRepository {

  override suspend fun castVote(sightingId: String, voterId: String): Result<Unit> =
      firestoreResult {
        val vote = votesOf(sightingId).document(voterId)
        // Read and write in one transaction, so two quick taps store a single vote
        val transaction = firestore.runTransaction { transaction ->
          if (transaction.get(vote).exists()) throw AlreadyVotedException(sightingId, voterId)
          transaction.set(
              vote,
              mapOf(VOTER_ID_FIELD to voterId, VOTED_AT_FIELD to FieldValue.serverTimestamp()),
          )
          Unit
        }
        Tasks.await(transaction)
      }

  override suspend fun getVoteCount(sightingId: String): Result<Int> = firestoreResult {
    // Counted by the server, without downloading the votes
    Tasks.await(votesOf(sightingId).count().get(AggregateSource.SERVER)).count.toInt()
  }

  override suspend fun hasVoted(sightingId: String, voterId: String): Result<Boolean> =
      firestoreResult {
        Tasks.await(votesOf(sightingId).document(voterId).get()).exists()
      }

  private fun votesOf(sightingId: String): CollectionReference =
      firestore.collection(SIGHTINGS_COLLECTION).document(sightingId).collection(VOTES_COLLECTION)

  /**
   * Runs [action] on [Dispatchers.IO] and returns its result. Errors become a [Result.failure]; an
   * [AlreadyVotedException] thrown in the transaction is given back as it is, even when Firestore
   * wraps it.
   */
  private suspend fun <T> firestoreResult(action: () -> T): Result<T> =
      try {
        Result.success(withContext(Dispatchers.IO) { action() })
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        val cause = if (exception is ExecutionException) exception.cause ?: exception else exception
        val alreadyVoted =
            generateSequence<Throwable>(cause) { it.cause }
                .firstOrNull { it is AlreadyVotedException }
        Result.failure(alreadyVoted ?: cause)
      }

  private companion object {
    const val SIGHTINGS_COLLECTION = "sightings"
    const val VOTES_COLLECTION = "votes"
    const val VOTER_ID_FIELD = "voterId"
    const val VOTED_AT_FIELD = "votedAt"
  }
}
