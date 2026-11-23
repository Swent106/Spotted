// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.fakes

import com.android.spotted.model.vote.AlreadyVotedException
import com.android.spotted.model.vote.VoteRepository

/**
 * In-memory [VoteRepository] for ViewModel tests.
 * - Like the real one, a second vote by the same user on the same sighting fails with
 *   [AlreadyVotedException] and stores nothing.
 * - [failure]: set it to make every call fail with it.
 * - The fake does not check who may vote (the Firestore rules do, #462).
 */
class FakeVoteRepository : VoteRepository {
  /** The voters of each sighting, by sighting id. */
  private val votersBySighting = mutableMapOf<String, MutableSet<String>>()

  var failure: Throwable? = null

  override suspend fun castVote(sightingId: String, voterId: String): Result<Unit> {
    failure?.let {
      return Result.failure(it)
    }
    val voters = votersBySighting.getOrPut(sightingId) { mutableSetOf() }
    if (!voters.add(voterId)) {
      return Result.failure(AlreadyVotedException(sightingId, voterId))
    }
    return Result.success(Unit)
  }

  override suspend fun getVoteCount(sightingId: String): Result<Int> {
    failure?.let {
      return Result.failure(it)
    }
    return Result.success(votersBySighting[sightingId].orEmpty().size)
  }

  override suspend fun hasVoted(sightingId: String, voterId: String): Result<Boolean> {
    failure?.let {
      return Result.failure(it)
    }
    return Result.success(voterId in votersBySighting[sightingId].orEmpty())
  }
}
