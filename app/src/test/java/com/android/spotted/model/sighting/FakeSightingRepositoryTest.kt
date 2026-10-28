// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.sighting

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.fakes.FakeSightingRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

  @Test
  fun observeSightingsForAlert_emitsCurrentListThenEveryChange() = runTest {
    val emissions = collectSightingsOf("alert-1")
    val older = sighting(id = "s-b", alertId = "alert-1", seenAtMillis = 1L)
    val sameTimeAsOlder = sighting(id = "s-a", alertId = "alert-1", seenAtMillis = 1L)
    val newer = sighting(id = "s-new", alertId = "alert-1", seenAtMillis = 2L)

    repository.reportSighting(older)
    repository.reportSighting(sameTimeAsOlder)
    repository.reportSighting(newer)
    repository.setSightingStatus("s-a", SightingStatus.CONFIRMED)

    assertEquals(
        listOf(
            emptyList(),
            listOf(older),
            listOf(sameTimeAsOlder, older),
            listOf(newer, sameTimeAsOlder, older),
            listOf(newer, sameTimeAsOlder.copy(status = SightingStatus.CONFIRMED), older),
        ),
        emissions.map { it.getOrThrow() },
    )
  }

  @Test
  fun observeSightingsForAlert_ignoresChangesToOtherAlertsAndUnlinkedSightings() = runTest {
    val emissions = collectSightingsOf("alert-1")

    repository.reportSighting(sighting(id = "s-other", alertId = "alert-2"))
    repository.reportSighting(sighting(id = "s-none", alertId = null))
    repository.setSightingStatus("s-other", SightingStatus.DISMISSED)

    assertEquals(listOf(Result.success(emptyList<Sighting>())), emissions)
  }

  @Test
  fun observeFailure_emitsFailureButOtherCallsStillWork() = runTest {
    val error = IllegalStateException("listener failed")
    repository.observeFailure = error

    val emissions = collectSightingsOf("alert-1")

    assertEquals(listOf(Result.failure<List<Sighting>>(error)), emissions)
    assertTrue(repository.reportSighting(sighting(id = "s1")).isSuccess)
    assertTrue(repository.getSightingsForAlert("alert-1").isSuccess)
  }

  @Test
  fun setSightingStatus_changesOnlyTheStatus() = runTest {
    val sighting = sighting(id = "s1", alertId = "alert-1")
    repository.reportSighting(sighting)

    assertTrue(repository.setSightingStatus("s1", SightingStatus.CONFIRMED).isSuccess)
    assertEquals(
        listOf(sighting.copy(status = SightingStatus.CONFIRMED)),
        repository.reportedSightings,
    )

    // The owner can change their mind
    assertTrue(repository.setSightingStatus("s1", SightingStatus.DISMISSED).isSuccess)
    assertEquals(SightingStatus.DISMISSED, repository.reportedSightings.single().status)
  }

  @Test
  fun setSightingStatus_failsWithoutChangingAnything() = runTest {
    val unlinked = sighting(id = "s-none", alertId = null)
    val linked = sighting(id = "s1", alertId = "alert-1")
    repository.reportSighting(unlinked)
    repository.reportSighting(linked)

    // Unknown sighting, and a sighting that is not linked to any alert
    assertTrue(repository.setSightingStatus("missing", SightingStatus.CONFIRMED).isFailure)
    assertTrue(repository.setSightingStatus("s-none", SightingStatus.CONFIRMED).isFailure)
    // Injected failure
    val error = IllegalStateException("status failed")
    repository.statusFailure = error
    assertSame(
        error,
        repository.setSightingStatus("s1", SightingStatus.CONFIRMED).exceptionOrNull(),
    )

    assertEquals(listOf(unlinked, linked), repository.reportedSightings)
  }

  @Test
  fun reportSighting_isPendingByDefault_andKeepsTheStatusItIsGiven() = runTest {
    repository.reportSighting(sighting(id = "s1"))
    repository.reportSighting(sighting(id = "s2").copy(status = SightingStatus.CONFIRMED))

    assertEquals(
        listOf(SightingStatus.PENDING, SightingStatus.CONFIRMED),
        repository.reportedSightings.map { it.status },
    )
  }

  /** Collects [FakeSightingRepository.observeSightingsForAlert] until the test ends. */
  private fun TestScope.collectSightingsOf(alertId: String): List<Result<List<Sighting>>> {
    val emissions = mutableListOf<Result<List<Sighting>>>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.observeSightingsForAlert(alertId).toList(emissions)
    }
    return emissions
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
