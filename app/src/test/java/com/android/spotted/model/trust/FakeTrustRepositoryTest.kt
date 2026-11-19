// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.trust

import com.android.spotted.model.fakes.FakeTrustRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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

  @Test
  fun applyVerdict_appliesItToTheTrustOfTheReporter() = runTest {
    val repository =
        FakeTrustRepository(mapOf("helper-1" to trusted), mapOf("sighting-1" to "helper-1"))

    val next = repository.applyVerdict("sighting-1", Verdict.FALSE).getOrThrow()

    assertEquals(trusted.after(Verdict.FALSE), next)
    assertEquals(next, repository.getTrust("helper-1").getOrThrow())
    assertEquals(mapOf("sighting-1" to Verdict.FALSE), repository.verdicts)
  }

  @Test
  fun applyVerdict_toAReporterWithNoTrust_startsFromInitial() = runTest {
    val repository = FakeTrustRepository()
    repository.addSighting("sighting-1", "helper-1")

    repository.applyVerdict("sighting-1", Verdict.CONFIRMED).getOrThrow()

    assertEquals(
        TrustScore.INITIAL.after(Verdict.CONFIRMED),
        repository.getTrust("helper-1").getOrThrow(),
    )
  }

  @Test
  fun applyVerdict_twiceOnTheSameSighting_failsAndKeepsTheFirst() = runTest {
    val repository = FakeTrustRepository(reporters = mapOf("sighting-1" to "helper-1"))
    val afterFirst = repository.applyVerdict("sighting-1", Verdict.CONFIRMED).getOrThrow()

    val error = repository.applyVerdict("sighting-1", Verdict.FALSE).exceptionOrNull()

    assertTrue(error is VerdictAlreadyAppliedException)
    assertEquals(afterFirst, repository.getTrust("helper-1").getOrThrow())
    assertEquals(mapOf("sighting-1" to Verdict.CONFIRMED), repository.verdicts)
  }

  @Test
  fun applyVerdict_ofAnUnknownSighting_fails() = runTest {
    val repository = FakeTrustRepository()

    val error = repository.applyVerdict("sighting-1", Verdict.CONFIRMED).exceptionOrNull()

    assertTrue(error is SightingNotFoundException)
  }

  @Test
  fun applyVerdict_whenFailing_returnsTheFailureAndChangesNothing() = runTest {
    val error = IllegalStateException("offline")
    val repository = FakeTrustRepository(reporters = mapOf("sighting-1" to "helper-1"))
    repository.failure = error

    assertSame(error, repository.applyVerdict("sighting-1", Verdict.CONFIRMED).exceptionOrNull())
    assertEquals(emptyMap<String, Verdict>(), repository.verdicts)
  }
}
