// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.vote

/**
 * "This looks false" votes on sightings: each user votes at most once per sighting.
 *
 * Who may vote is checked by the Firestore rules (#462). Each vote weighs the voter's trust score,
 * and the vote that brings the total to [VoteRules.THRESHOLD] also hides the sighting and applies a
 * false verdict to its reporter, in the same write (#463).
 *
 * Contract shared by every implementation (Firestore and fakes):
 * - errors are returned as [Result.failure], never thrown to the caller;
 * - implementations are main-safe (blocking I/O runs off the main thread).
 */
interface VoteRepository {

  /**
   * Records that [voterId] thinks the sighting [sightingId] is false. A user votes once per
   * sighting: a second vote fails with [AlreadyVotedException] and stores nothing.
   */
  suspend fun castVote(sightingId: String, voterId: String): Result<Unit>

  /** The number of votes on the sighting [sightingId]: 0 when nobody voted. */
  suspend fun getVoteCount(sightingId: String): Result<Int>

  /** Whether [voterId] already voted on the sighting [sightingId]. */
  suspend fun hasVoted(sightingId: String, voterId: String): Result<Boolean>
}

/** [voterId] already voted on the sighting [sightingId], so their new vote was not stored. */
class AlreadyVotedException(val sightingId: String, val voterId: String) :
    IllegalStateException("$voterId already voted on sighting $sightingId")
