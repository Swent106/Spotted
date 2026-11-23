// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.vote

import com.android.spotted.model.vote.AlreadyVotedException
import com.android.spotted.model.vote.VoteRepository
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
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
 * the fields `voterId` and `votedAt` (the server time). The document id is the voter's uid, so a
 * user has at most one vote per sighting.
 *
 * Each vote also updates the voter's `voteQuota/{voterId}`, the times of their last 10 votes, in
 * the same transaction: the Firestore rules read it to allow at most 10 votes in 24 hours, and
 * refuse a vote that doesn't update it. The rules also refuse a vote from the reporter, from an
 * account less than 7 days old, or from a user below Trusted: [castVote] then fails with the
 * Firestore permission error.
 */
class VoteRepositoryFirestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : VoteRepository {

  override suspend fun castVote(sightingId: String, voterId: String): Result<Unit> =
      firestoreResult {
        val vote = votesOf(sightingId).document(voterId)
        val quota = firestore.collection(VOTE_QUOTA_COLLECTION).document(voterId)
        // Read and write in one transaction, so two quick taps store a single vote and the quota
        // counts each vote once
        val transaction = firestore.runTransaction { transaction ->
          if (transaction.get(vote).exists()) throw AlreadyVotedException(sightingId, voterId)
          val quotaBefore = transaction.get(quota)
          val votesBefore =
              quotaBefore.get(RECENT_FIELD).asTimestamps() +
                  listOfNotNull(quotaBefore.getTimestamp(LAST_FIELD))
          transaction.set(
              vote,
              mapOf(VOTER_ID_FIELD to voterId, VOTED_AT_FIELD to FieldValue.serverTimestamp()),
          )
          transaction.set(
              quota,
              mapOf(
                  // The previous latest vote joins the 9 before it; this vote is the latest
                  RECENT_FIELD to votesBefore.takeLast(MAX_VOTES_PER_DAY - 1),
                  LAST_FIELD to FieldValue.serverTimestamp(),
                  LAST_SIGHTING_ID_FIELD to sightingId,
              ),
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

  /** The timestamps of a stored list; empty when the quota doesn't exist yet. */
  private fun Any?.asTimestamps(): List<Timestamp> =
      (this as? List<*>).orEmpty().filterIsInstance<Timestamp>()

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
    const val VOTE_QUOTA_COLLECTION = "voteQuota"
    const val VOTER_ID_FIELD = "voterId"
    const val VOTED_AT_FIELD = "votedAt"
    const val RECENT_FIELD = "recent"
    const val LAST_FIELD = "last"
    const val LAST_SIGHTING_ID_FIELD = "lastSightingId"

    /** At most this many votes in 24 hours: the limit in the voteQuota rules. */
    const val MAX_VOTES_PER_DAY = 10
  }
}
