// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.thanks

import com.android.spotted.model.fakes.FakeThanksRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeThanksRepositoryTest {
  private val repository = FakeThanksRepository()

  private fun thanks(
      alertId: String = "alert-1",
      helperId: String = "helper-1",
      thankedAtMillis: Long = 1_000L,
  ) =
      Thanks(
          alertId = alertId,
          ownerId = "owner-1",
          helperId = helperId,
          sightingId = "sighting-$alertId-$helperId",
          petName = "Milo",
          thankedAtMillis = thankedAtMillis,
      )

  @Test
  fun idOf_isBuiltFromAlertAndHelper() {
    assertEquals("alert-1_helper-1", thanks().id)
  }

  @Test
  fun thank_isCounted() = runTest {
    assertTrue(repository.thank(thanks()).isSuccess)

    assertEquals(1, repository.getThanksCount("helper-1").getOrThrow())
  }

  @Test
  fun neverThanked_countsZero() = runTest {
    assertEquals(0, repository.getThanksCount("helper-1").getOrThrow())
  }

  @Test
  fun thankingTwiceForTheSameAlert_failsAndCountsOnce() = runTest {
    repository.thank(thanks()).getOrThrow()

    val result = repository.thank(thanks(thankedAtMillis = 2_000L))

    val error = result.exceptionOrNull() as? AlreadyThankedException
    assertEquals("alert-1", error?.alertId)
    assertEquals("helper-1", error?.helperId)
    assertEquals(1, repository.getThanksCount("helper-1").getOrThrow())
  }

  @Test
  fun thanksForTwoAlerts_countTwice() = runTest {
    repository.thank(thanks(alertId = "alert-1")).getOrThrow()
    repository.thank(thanks(alertId = "alert-2")).getOrThrow()
    repository.thank(thanks(helperId = "helper-2")).getOrThrow()

    assertEquals(2, repository.getThanksCount("helper-1").getOrThrow())
  }

  @Test
  fun thankingYourself_fails() = runTest {
    val result = repository.thank(thanks(helperId = "owner-1"))

    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    assertEquals(0, repository.getThanksCount("owner-1").getOrThrow())
  }

  @Test
  fun observeThanksReceived_listsOnlyTheHelpersThanks_newestFirst() = runTest {
    val older = thanks(alertId = "alert-1", thankedAtMillis = 1_000L)
    val newer = thanks(alertId = "alert-2", thankedAtMillis = 2_000L)
    repository.thank(older).getOrThrow()
    repository.thank(newer).getOrThrow()
    repository.thank(thanks(helperId = "helper-2")).getOrThrow()

    val received = repository.observeThanksReceived("helper-1").first().getOrThrow()

    assertEquals(listOf(newer, older), received)
  }

  @Test
  fun failure_isReturnedByEveryCall() = runTest {
    val expected = IllegalStateException("offline")
    repository.failure = expected

    assertSame(expected, repository.thank(thanks()).exceptionOrNull())
    assertSame(expected, repository.getThanksCount("helper-1").exceptionOrNull())
    assertSame(
        expected,
        repository.observeThanksReceived("helper-1").first().exceptionOrNull(),
    )
  }
}
