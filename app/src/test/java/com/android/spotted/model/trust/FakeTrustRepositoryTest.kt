// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.trust

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.fakes.FakeSightingRepository
import com.android.spotted.model.fakes.FakeTrustRepository
import com.android.spotted.model.sighting.Sighting
import com.android.spotted.model.sighting.SightingStatus
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
  fun confirmSighting_ofAReporterWithNoTrust_startsFromInitial() = runTest {
    val repository = FakeTrustRepository()
    repository.addSighting("sighting-1", "helper-1")

    repository.confirmSighting("sighting-1").getOrThrow()

    assertEquals(
        TrustScore.INITIAL.after(Verdict.CONFIRMED),
        repository.getTrust("helper-1").getOrThrow(),
    )
    assertEquals(mapOf("sighting-1" to Verdict.CONFIRMED), repository.verdicts)
  }

  @Test
  fun confirmSighting_twice_countsItOnce() = runTest {
    val repository =
        FakeTrustRepository(mapOf("helper-1" to trusted), mapOf("sighting-1" to "helper-1"))

    repository.confirmSighting("sighting-1").getOrThrow()
    repository.confirmSighting("sighting-1").getOrThrow()

    assertEquals(trusted.after(Verdict.CONFIRMED), repository.getTrust("helper-1").getOrThrow())
    assertEquals(mapOf("sighting-1" to Verdict.CONFIRMED), repository.verdicts)
  }

  @Test
  fun confirmSighting_ofAnUnknownSighting_fails() = runTest {
    val repository = FakeTrustRepository(sightings = FakeSightingRepository())

    val error = repository.confirmSighting("sighting-1").exceptionOrNull()

    assertTrue(error is SightingNotFoundException)
    assertTrue(repository.verdicts.isEmpty())
  }

  @Test
  fun confirmSighting_withSightings_confirmsItThereAndFindsItsReporter() = runTest {
    val sightings = FakeSightingRepository()
    sightings.reportSighting(SIGHTING)
    val repository = FakeTrustRepository(sightings = sightings)

    repository.confirmSighting(SIGHTING.id).getOrThrow()

    assertEquals(SightingStatus.CONFIRMED, sightings.reportedSightings.single().status)
    assertEquals(
        TrustScore.INITIAL.after(Verdict.CONFIRMED),
        repository.getTrust("helper-1").getOrThrow(),
    )
  }

  @Test
  fun confirmSighting_whenTheStatusFails_changesNothing() = runTest {
    val sightings = FakeSightingRepository()
    sightings.reportSighting(SIGHTING)
    val error = IllegalStateException("offline")
    sightings.statusFailure = error
    val repository = FakeTrustRepository(sightings = sightings)

    assertSame(error, repository.confirmSighting(SIGHTING.id).exceptionOrNull())

    assertEquals(SightingStatus.PENDING, sightings.reportedSightings.single().status)
    assertEquals(TrustScore.INITIAL, repository.getTrust("helper-1").getOrThrow())
    assertTrue(repository.verdicts.isEmpty())
  }

  @Test
  fun confirmSighting_whenFailing_returnsTheFailureAndChangesNothing() = runTest {
    val error = IllegalStateException("offline")
    val repository = FakeTrustRepository(reporters = mapOf("sighting-1" to "helper-1"))
    repository.failure = error

    assertSame(error, repository.confirmSighting("sighting-1").exceptionOrNull())

    repository.failure = null
    assertTrue(repository.verdicts.isEmpty())
    assertEquals(TrustScore.INITIAL, repository.getTrust("helper-1").getOrThrow())
  }

  private companion object {
    val SIGHTING =
        Sighting(
            id = "sighting-1",
            reporterId = "helper-1",
            location = Location(latitude = 46.5, longitude = 6.6),
            seenAtMillis = 1_000L,
            species = Species.DOG,
            alertId = "alert-1",
        )
  }
}
