package com.android.spotted.model.alert

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.distanceKm
import com.android.spotted.model.fakes.FakeAlertRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAlertRepositoryTest {
  private val repository = FakeAlertRepository()
  private val center = Location(latitude = 0.0, longitude = 0.0)

  @Test
  fun getNewId_returnsDeterministicUniqueIds() {
    assertEquals("alert-1", repository.getNewId())
    assertEquals("alert-2", repository.getNewId())
  }

  @Test
  fun publishAlert_storesAlertAndReturnsSuccess() = runTest {
    val alert = alert(id = "alert-1", latitude = 0.0, longitude = 0.0)

    assertTrue(repository.publishAlert(alert).isSuccess)
    assertEquals(listOf(alert), repository.publishedAlerts)
    assertEquals(listOf(alert), repository.getOpenAlertsNear(center, radiusKm = 1.0).getOrThrow())
  }

  @Test
  fun getOpenAlertsNear_filtersClosedAndOutOfRadiusAlerts_andSortsNewestFirst() = runTest {
    val olderNearby =
        alert(id = "alert-older", lostAtMillis = 1L, latitude = 0.0, longitude = 0.001)
    val newerNearby =
        alert(id = "alert-newer", lostAtMillis = 2L, latitude = 0.0, longitude = 0.002)
    val closedNearby =
        alert(id = "alert-closed", lostAtMillis = 3L, latitude = 0.0, longitude = 0.0)
            .copy(status = AlertStatus.CLOSED)
    val outsideRadius = alert(id = "alert-far", lostAtMillis = 4L, latitude = 0.0, longitude = 0.02)
    listOf(olderNearby, newerNearby, closedNearby, outsideRadius).forEach {
      repository.publishAlert(it)
    }

    val result = repository.getOpenAlertsNear(center, radiusKm = 1.0)

    assertEquals(listOf(newerNearby, olderNearby), result.getOrThrow())
  }

  @Test
  fun getOpenAlertsNear_includesAlertsExactlyAtRadius() = runTest {
    val boundaryAlert = alert(id = "alert-boundary", latitude = 0.0, longitude = 0.01)
    repository.publishAlert(boundaryAlert)
    val radiusKm = center.distanceKm(boundaryAlert.lastKnownLocation)

    assertEquals(
        listOf(boundaryAlert),
        repository.getOpenAlertsNear(center, radiusKm).getOrThrow(),
    )
  }

  @Test
  fun publishAlert_returnsConfiguredFailureWithoutStoringAlert() = runTest {
    val expectedFailure = IllegalStateException("publish failed")
    repository.failure = expectedFailure
    val alert = alert(id = "alert-1", latitude = 0.0, longitude = 0.0)

    val result = repository.publishAlert(alert)

    assertSame(expectedFailure, result.exceptionOrNull())
    assertTrue(repository.publishedAlerts.isEmpty())
  }

  @Test
  fun getOpenAlertsNear_returnsConfiguredFailure() = runTest {
    val expectedFailure = IllegalStateException("query failed")
    repository.failure = expectedFailure

    val result = repository.getOpenAlertsNear(center, radiusKm = 1.0)

    assertSame(expectedFailure, result.exceptionOrNull())
  }

  @Test
  fun getOpenAlertsNear_returnsEmptyListWhenNoAlertsAreNearby() = runTest {
    repository.publishAlert(alert(id = "alert-far", latitude = 10.0, longitude = 10.0))

    assertEquals(
        emptyList<Alert>(),
        repository.getOpenAlertsNear(center, radiusKm = 1.0).getOrThrow(),
    )
  }

  private fun alert(
      id: String,
      lostAtMillis: Long = 1L,
      latitude: Double,
      longitude: Double,
  ): Alert =
      Alert(
          id = id,
          petId = "pet-$id",
          ownerId = "owner-1",
          lastKnownLocation = Location(latitude = latitude, longitude = longitude),
          lostAtMillis = lostAtMillis,
          petName = "Buddy",
          petSpecies = Species.DOG,
      )
}
