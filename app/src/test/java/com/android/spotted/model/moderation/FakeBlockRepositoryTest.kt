// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.moderation

import com.android.spotted.model.fakes.FakeBlockRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeBlockRepositoryTest {

  private val repository = FakeBlockRepository()

  private suspend fun blockedBy(userId: String) =
      repository.observeBlocked(userId).first().getOrThrow()

  @Test
  fun block_addsToThatUsersListOnly_andTwiceIsFine() = runTest {
    assertTrue(repository.block("alice", "spammer").isSuccess)
    assertTrue(repository.block("alice", "spammer").isSuccess)

    assertEquals(setOf("spammer"), blockedBy("alice"))
    assertEquals(emptySet<String>(), blockedBy("bob"))
  }

  @Test
  fun unblock_removes_andAUserNotBlockedIsFine() = runTest {
    repository.block("alice", "spammer")

    assertTrue(repository.unblock("alice", "spammer").isSuccess)
    assertTrue(repository.unblock("alice", "spammer").isSuccess)

    assertEquals(emptySet<String>(), blockedBy("alice"))
  }

  @Test
  fun blockingYourself_fails() = runTest {
    val result = repository.block("alice", "alice")

    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    assertEquals(emptySet<String>(), repository.blockedBy("alice"))
  }

  @Test
  fun failure_makesEveryCallFail() = runTest {
    repository.failure = IllegalStateException("offline")

    assertTrue(repository.block("alice", "spammer").isFailure)
    assertTrue(repository.unblock("alice", "spammer").isFailure)
    assertTrue(repository.observeBlocked("alice").first().isFailure)
  }
}
