// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.vote

import com.android.spotted.model.fakes.FakeVoteRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks that the fake follows the [VoteRepository] contract the ViewModel tests rely on. */
class FakeVoteRepositoryTest {
  private val repository = FakeVoteRepository()

  @Test
  fun castVote_firstVote_succeedsAndIsCounted() = runTest {
    assertTrue(repository.castVote(SIGHTING, ALICE).isSuccess)

    assertEquals(1, repository.getVoteCount(SIGHTING).getOrThrow())
  }

  @Test
  fun castVote_sameVoterTwice_failsWithAlreadyVotedAndCountStaysOne() = runTest {
    repository.castVote(SIGHTING, ALICE).getOrThrow()

    val error = repository.castVote(SIGHTING, ALICE).exceptionOrNull()

    assertTrue(error is AlreadyVotedException)
    error as AlreadyVotedException
    assertEquals(SIGHTING, error.sightingId)
    assertEquals(ALICE, error.voterId)
    assertEquals(1, repository.getVoteCount(SIGHTING).getOrThrow())
  }

  @Test
  fun castVote_twoVoters_countIsTwo() = runTest {
    repository.castVote(SIGHTING, ALICE).getOrThrow()
    repository.castVote(SIGHTING, BOB).getOrThrow()

    assertEquals(2, repository.getVoteCount(SIGHTING).getOrThrow())
  }

  @Test
  fun getVoteCount_sightingWithoutVotes_returnsZero() = runTest {
    assertEquals(0, repository.getVoteCount(SIGHTING).getOrThrow())
  }

  @Test
  fun getVoteCount_countsOnlyVotesOfThatSighting() = runTest {
    repository.castVote(SIGHTING, ALICE).getOrThrow()
    repository.castVote(OTHER_SIGHTING, ALICE).getOrThrow()
    repository.castVote(OTHER_SIGHTING, BOB).getOrThrow()

    assertEquals(1, repository.getVoteCount(SIGHTING).getOrThrow())
    assertEquals(2, repository.getVoteCount(OTHER_SIGHTING).getOrThrow())
  }

  @Test
  fun hasVoted_beforeVoting_returnsFalse() = runTest {
    assertFalse(repository.hasVoted(SIGHTING, ALICE).getOrThrow())
  }

  @Test
  fun hasVoted_afterVoting_returnsTrue() = runTest {
    repository.castVote(SIGHTING, ALICE).getOrThrow()

    assertTrue(repository.hasVoted(SIGHTING, ALICE).getOrThrow())
  }

  @Test
  fun hasVoted_otherUserVoted_returnsFalse() = runTest {
    repository.castVote(SIGHTING, BOB).getOrThrow()

    assertFalse(repository.hasVoted(SIGHTING, ALICE).getOrThrow())
  }

  @Test
  fun failure_makesEveryCallFailWithoutStoringAVote() = runTest {
    val error = IllegalStateException("network down")
    repository.failure = error

    assertSame(error, repository.castVote(SIGHTING, ALICE).exceptionOrNull())
    assertSame(error, repository.getVoteCount(SIGHTING).exceptionOrNull())
    assertSame(error, repository.hasVoted(SIGHTING, ALICE).exceptionOrNull())

    repository.failure = null
    assertEquals(0, repository.getVoteCount(SIGHTING).getOrThrow())
  }

  private companion object {
    const val SIGHTING = "sighting-1"
    const val OTHER_SIGHTING = "sighting-2"
    const val ALICE = "alice"
    const val BOB = "bob"
  }
}
