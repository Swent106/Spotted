// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.thanks

import com.android.spotted.model.fakes.FakeThanksRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
  fun id_isBuiltFromAlertAndHelper() {
    assertEquals("alert-1_helper-1", thanks().id)
  }

  @Test
  fun thank_newThanks_isCounted() = runTest {
    assertTrue(repository.thank(thanks()).isSuccess)

    assertEquals(1, repository.getThanksCount("helper-1").getOrThrow())
  }

  @Test
  fun getThanksCount_neverThanked_returnsZero() = runTest {
    assertEquals(0, repository.getThanksCount("helper-1").getOrThrow())
  }

  @Test
  fun thank_sameAlertTwice_failsAndKeepsTheFirst() = runTest {
    repository.thank(thanks()).getOrThrow()

    val error = repository.thank(thanks(thankedAtMillis = 2_000L)).exceptionOrNull()

    assertTrue("Expected AlreadyThankedException, got $error", error is AlreadyThankedException)
    error as AlreadyThankedException
    assertEquals("alert-1", error.alertId)
    assertEquals("helper-1", error.helperId)
    // single(): still one thanks, and it was not overwritten by the second one
    val stored = repository.observeThanksReceived("helper-1").first().getOrThrow()
    assertEquals(1_000L, stored.single().thankedAtMillis)
  }

  @Test
  fun getThanksCount_countsOnlyThatHelper_acrossAlerts() = runTest {
    repository.thank(thanks(alertId = "alert-1")).getOrThrow()
    repository.thank(thanks(alertId = "alert-2")).getOrThrow()
    repository.thank(thanks(helperId = "helper-2")).getOrThrow()

    assertEquals(2, repository.getThanksCount("helper-1").getOrThrow())
  }

  @Test
  fun thank_yourself_fails() = runTest {
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
  fun observeThanksReceived_newThanks_emitsAgain() = runTest {
    val emissions = mutableListOf<List<Thanks>>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.observeThanksReceived("helper-1").collect { emissions += it.getOrThrow() }
    }

    repository.thank(thanks()).getOrThrow()

    assertEquals(listOf(emptyList(), listOf(thanks())), emissions)
  }

  @Test
  fun observeThanksReceived_sameTime_sortsById() = runTest {
    val second = thanks(alertId = "alert-2")
    val first = thanks(alertId = "alert-1")
    repository.thank(second).getOrThrow()
    repository.thank(first).getOrThrow()

    val received = repository.observeThanksReceived("helper-1").first().getOrThrow()

    assertEquals(listOf(first, second), received)
  }

  @Test
  fun observeThanksReceived_overTheMax_keepsTheNewest() = runTest {
    val max = ThanksRepository.MAX_THANKS_RECEIVED
    repeat(max + 1) { i ->
      repository.thank(thanks(alertId = "alert-$i", thankedAtMillis = i.toLong())).getOrThrow()
    }

    val received = repository.observeThanksReceived("helper-1").first().getOrThrow()

    assertEquals(max, received.size)
    assertEquals("alert-$max", received.first().alertId)
    assertTrue(received.none { it.alertId == "alert-0" })
  }

  @Test
  fun allCalls_failureSet_returnTheFailure() = runTest {
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
