// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.trust

import com.android.spotted.model.fakes.FakeTrustRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FakeTrustRepositoryTest {
  private val trusted = TrustScore(score = 0.6, judged = 5)

  @Test
  fun getTrust_returnsTheStoredTrust() = runTest {
    val repository = FakeTrustRepository(mapOf("helper-1" to trusted))

    assertEquals(trusted, repository.getTrust("helper-1").getOrThrow())
  }

  @Test
  fun getTrust_ofAUserWithNoTrust_isInitial() = runTest {
    val repository = FakeTrustRepository(mapOf("helper-1" to trusted))

    assertEquals(TrustScore.INITIAL, repository.getTrust("helper-2").getOrThrow())
  }

  @Test
  fun setTrust_replacesTheStoredTrust() = runTest {
    val repository = FakeTrustRepository(mapOf("helper-1" to trusted))
    val afterFalse = trusted.after(Verdict.FALSE)

    repository.setTrust("helper-1", afterFalse)

    assertEquals(afterFalse, repository.getTrust("helper-1").getOrThrow())
  }

  @Test
  fun getTrust_whenFailing_returnsTheFailure() = runTest {
    val error = IllegalStateException("offline")
    val repository = FakeTrustRepository(mapOf("helper-1" to trusted))
    repository.failure = error

    assertSame(error, repository.getTrust("helper-1").exceptionOrNull())
  }
}
