// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.notification

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.fakes.FakeAlertRepository
import com.android.spotted.model.fakes.FakeSightingRepository
import com.android.spotted.model.notification.SightingNotification
import com.android.spotted.model.sighting.Sighting
import com.android.spotted.model.sighting.SightingStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationRepositoryDefaultTest {

  private val alerts = FakeAlertRepository()
  private val sightings = FakeSightingRepository()
  private val repository = NotificationRepositoryDefault(alerts, sightings)

  private suspend fun pending(ownerId: String = OWNER) =
      repository.observePendingSightings(ownerId).first().getOrThrow()

  @Test
  fun noOpenAlert_noNotification() = runTest {
    assertEquals(emptyList<SightingNotification>(), pending())
  }

  @Test
  fun oneAlert_givesItsPendingSightingsWithThePetName() = runTest {
    alerts.publishAlert(MILO)
    sightings.reportSighting(sighting("s1", MILO, seenAt = 100L, photoUrl = "https://photo/s1"))

    assertEquals(
        listOf(SightingNotification("s1", MILO.id, "Milo", "https://photo/s1", 100L)),
        pending(),
    )
  }

  @Test
  fun twoAlerts_areMergedNewestFirst() = runTest {
    alerts.publishAlert(MILO)
    alerts.publishAlert(REX)
    sightings.reportSighting(sighting("s1", MILO, seenAt = 100L))
    sightings.reportSighting(sighting("s2", REX, seenAt = 300L))
    sightings.reportSighting(sighting("s3", MILO, seenAt = 200L))

    assertEquals(listOf("s2", "s3", "s1"), pending().map { it.sightingId })
    assertEquals(listOf("Rex", "Milo", "Milo"), pending().map { it.petName })
  }

  @Test
  fun sameTime_isOrderedBySightingId() = runTest {
    alerts.publishAlert(MILO)
    sightings.reportSighting(sighting("b", MILO, seenAt = 100L))
    sightings.reportSighting(sighting("a", MILO, seenAt = 100L))

    assertEquals(listOf("a", "b"), pending().map { it.sightingId })
  }

  @Test
  fun reviewedSightings_areLeftOut() = runTest {
    alerts.publishAlert(MILO)
    sightings.reportSighting(sighting("confirmed", MILO, seenAt = 100L))
    sightings.reportSighting(sighting("dismissed", MILO, seenAt = 200L))
    sightings.reportSighting(sighting("pending", MILO, seenAt = 300L))
    sightings.setSightingStatus("confirmed", SightingStatus.CONFIRMED)
    sightings.setSightingStatus("dismissed", SightingStatus.DISMISSED)

    assertEquals(listOf("pending"), pending().map { it.sightingId })
  }

  @Test
  fun otherOwnersAndClosedAlerts_areLeftOut() = runTest {
    alerts.publishAlert(MILO)
    alerts.publishAlert(OTHER_OWNER_ALERT)
    alerts.publishAlert(CLOSED_SOON)
    alerts.closeAlert(CLOSED_SOON.id)
    sightings.reportSighting(sighting("mine", MILO, seenAt = 100L))
    sightings.reportSighting(sighting("theirs", OTHER_OWNER_ALERT, seenAt = 200L))
    sightings.reportSighting(sighting("closed", CLOSED_SOON, seenAt = 300L))

    assertEquals(listOf("mine"), pending().map { it.sightingId })
  }

  @Test
  fun newAndReviewedSightings_updateTheListLive() = runTest {
    alerts.publishAlert(MILO)
    val emitted = mutableListOf<List<String>>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.observePendingSightings(OWNER).collect { result ->
        emitted += result.getOrThrow().map { it.sightingId }
      }
    }

    sightings.reportSighting(sighting("s1", MILO, seenAt = 100L))
    sightings.setSightingStatus("s1", SightingStatus.CONFIRMED)

    assertEquals(listOf(emptyList(), listOf("s1"), emptyList<String>()), emitted)
  }

  @Test
  fun alertsFailure_isEmittedAsFailure() = runTest {
    val error = IllegalStateException("offline")
    alerts.failure = error

    val result = repository.observePendingSightings(OWNER).first()

    assertTrue(result.isFailure)
    assertEquals(error, result.exceptionOrNull())
  }

  @Test
  fun sightingsFailure_isEmittedAsFailure() = runTest {
    alerts.publishAlert(MILO)
    val error = IllegalStateException("permission denied")
    sightings.observeFailure = error

    val result = repository.observePendingSightings(OWNER).first()

    assertEquals(error, result.exceptionOrNull())
  }

  @Test
  fun failureOnOneAlert_failsTheWholeList() = runTest {
    alerts.publishAlert(MILO)
    alerts.publishAlert(REX)
    sightings.reportSighting(sighting("s1", MILO, seenAt = 100L))
    val error = IllegalStateException("listener failed")
    val emitted = mutableListOf<Result<List<SightingNotification>>>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.observePendingSightings(OWNER).collect { emitted += it }
    }

    sightings.emitObservationFailure(error)

    assertEquals(listOf("s1"), emitted.first().getOrThrow().map { it.sightingId })
    assertEquals(error, emitted.last().exceptionOrNull())
  }

  private companion object {
    const val OWNER = "owner"
    val PARC = Location(latitude = 46.1925, longitude = 6.1580)

    fun alert(id: String, petName: String, ownerId: String = OWNER, lostAt: Long = 0L) =
        Alert(
            id = id,
            petId = "pet-$id",
            ownerId = ownerId,
            lastKnownLocation = PARC,
            lostAtMillis = lostAt,
            petName = petName,
            petSpecies = Species.CAT,
        )

    val MILO = alert("alert-milo", "Milo", lostAt = 10L)
    val REX = alert("alert-rex", "Rex", lostAt = 20L)
    val OTHER_OWNER_ALERT = alert("alert-other", "Luna", ownerId = "someone-else")
    val CLOSED_SOON = alert("alert-closed", "Kiwi")

    fun sighting(id: String, alert: Alert, seenAt: Long, photoUrl: String? = null) =
        Sighting(
            id = id,
            reporterId = "helper",
            location = PARC,
            seenAtMillis = seenAt,
            species = Species.CAT,
            photoUrl = photoUrl,
            alertId = alert.id,
        )
  }
}
