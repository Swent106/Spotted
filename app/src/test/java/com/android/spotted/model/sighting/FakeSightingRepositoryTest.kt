package com.android.spotted.model.sighting

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.fakes.FakeSightingRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks that the fake follows the [SightingRepository] contract the ViewModel tests rely on. */
class FakeSightingRepositoryTest {
  private val repository = FakeSightingRepository()

  @Test
  fun getNewId_returnsDeterministicUniqueIds() {
    assertEquals("sighting-1", repository.getNewId())
    assertEquals("sighting-2", repository.getNewId())
  }

  @Test
  fun reportSighting_storesSightingAndReturnsSuccess() = runTest {
    val sighting = sighting(id = "s1", alertId = "alert-1")

    assertTrue(repository.reportSighting(sighting).isSuccess)
    assertEquals(listOf(sighting), repository.reportedSightings)
  }

  @Test
  fun reportSighting_withSameId_replacesPreviousSighting() = runTest {
    repository.reportSighting(sighting(id = "s1", seenAtMillis = 1L))
    val updated = sighting(id = "s1", seenAtMillis = 2L)

    repository.reportSighting(updated)

    assertEquals(listOf(updated), repository.reportedSightings)
  }

  @Test
  fun getSightingsForAlert_returnsOnlyThatAlert_newestFirst() = runTest {
    val older = sighting(id = "s-old", alertId = "alert-1", seenAtMillis = 1L)
    val newer = sighting(id = "s-new", alertId = "alert-1", seenAtMillis = 2L)
    val otherAlert = sighting(id = "s-other", alertId = "alert-2", seenAtMillis = 3L)
    val unlinked = sighting(id = "s-none", alertId = null, seenAtMillis = 4L)
    listOf(older, newer, otherAlert, unlinked).forEach { repository.reportSighting(it) }

    assertEquals(listOf(newer, older), repository.getSightingsForAlert("alert-1").getOrThrow())
  }

  @Test
  fun getSightingsForAlert_withNoSightings_returnsEmptyList() = runTest {
    assertEquals(emptyList<Sighting>(), repository.getSightingsForAlert("alert-1").getOrThrow())
  }

  @Test
  fun reportFailure_failsReportWithoutStoring() = runTest {
    val error = IllegalStateException("report failed")
    repository.reportFailure = error

    val result = repository.reportSighting(sighting(id = "s1"))

    assertSame(error, result.exceptionOrNull())
    assertTrue(repository.reportedSightings.isEmpty())
  }

  @Test
  fun queryFailure_failsQueryButNotReport() = runTest {
    val error = IllegalStateException("query failed")
    repository.queryFailure = error

    assertTrue(repository.reportSighting(sighting(id = "s1", alertId = "alert-1")).isSuccess)
    assertSame(error, repository.getSightingsForAlert("alert-1").exceptionOrNull())
  }

  private fun sighting(
      id: String,
      alertId: String? = "alert-1",
      seenAtMillis: Long = 1L,
  ) =
      Sighting(
          id = id,
          reporterId = "passerby-1",
          location = Location(latitude = 46.5197, longitude = 6.6323),
          seenAtMillis = seenAtMillis,
          species = Species.DOG,
          alertId = alertId,
      )
}
