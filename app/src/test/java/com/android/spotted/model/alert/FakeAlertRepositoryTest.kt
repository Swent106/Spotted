// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.alert

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.distanceKm
import com.android.spotted.model.fakes.FakeAlertRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAlertRepositoryTest {
  private var nowMillis = 0L
  private val repository = FakeAlertRepository(now = { nowMillis })
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
  fun getAlertById_returnsMatchingAlert() = runTest {
    val alert = alert(id = "alert-1", latitude = 0.0, longitude = 0.0)
    repository.publishAlert(alert)

    assertEquals(alert, repository.getAlertById(alert.id).getOrThrow())
  }

  @Test
  fun getAlertById_returnsNullWhenMissing() = runTest {
    assertEquals(null, repository.getAlertById("missing").getOrThrow())
  }

  @Test
  fun getAlertById_returnsConfiguredFailure() = runTest {
    val expectedFailure = IllegalStateException("lookup failed")
    repository.failure = expectedFailure

    val result = repository.getAlertById("alert-1")

    assertSame(expectedFailure, result.exceptionOrNull())
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

  @Test
  fun getMyAlerts_returnsOpenAndClosedAlertsOfOwnerOnly_newestFirst() = runTest {
    val older = alert(id = "alert-older", lostAtMillis = 1L, latitude = 0.0, longitude = 0.0)
    val newerClosed =
        alert(id = "alert-closed", lostAtMillis = 2L, latitude = 0.0, longitude = 0.0)
            .copy(status = AlertStatus.CLOSED, closedAtMillis = 3L)
    val someoneElses =
        alert(id = "alert-other", latitude = 0.0, longitude = 0.0, ownerId = "owner-2")
    listOf(older, newerClosed, someoneElses).forEach { repository.publishAlert(it) }

    assertEquals(listOf(newerClosed, older), repository.getMyAlerts("owner-1").getOrThrow())
  }

  @Test
  fun getMyAlerts_returnsConfiguredFailure() = runTest {
    val expectedFailure = IllegalStateException("query failed")
    repository.failure = expectedFailure

    assertSame(expectedFailure, repository.getMyAlerts("owner-1").exceptionOrNull())
  }

  @Test
  fun closeAlert_closesAlertAtCurrentTime_andHidesItFromOpenAlerts() = runTest {
    val alert = alert(id = "alert-1", latitude = 0.0, longitude = 0.0)
    repository.publishAlert(alert)
    nowMillis = 42L

    assertTrue(repository.closeAlert(alert.id).isSuccess)

    val closed = repository.getAlertById(alert.id).getOrThrow()
    assertEquals(alert.copy(status = AlertStatus.CLOSED, closedAtMillis = 42L), closed)
    assertTrue(repository.getOpenAlertsNear(center, radiusKm = 1.0).getOrThrow().isEmpty())
  }

  @Test
  fun closeAlert_onClosedAlert_keepsOriginalClosingTime() = runTest {
    val alert = alert(id = "alert-1", latitude = 0.0, longitude = 0.0)
    repository.publishAlert(alert)
    nowMillis = 42L
    repository.closeAlert(alert.id)
    nowMillis = 99L

    assertTrue(repository.closeAlert(alert.id).isSuccess)

    assertEquals(42L, repository.getAlertById(alert.id).getOrThrow()?.closedAtMillis)
  }

  @Test
  fun closeAlert_returnsFailureWhenAlertIsMissing() = runTest {
    val result = repository.closeAlert("missing")

    assertTrue(result.exceptionOrNull() is NoSuchElementException)
  }

  @Test
  fun closeAlert_returnsConfiguredFailureWithoutClosing() = runTest {
    val alert = alert(id = "alert-1", latitude = 0.0, longitude = 0.0)
    repository.publishAlert(alert)
    val expectedFailure = IllegalStateException("update failed")
    repository.failure = expectedFailure

    assertSame(expectedFailure, repository.closeAlert(alert.id).exceptionOrNull())

    repository.failure = null
    val stored = repository.getAlertById(alert.id).getOrThrow()
    assertEquals(AlertStatus.OPEN, stored?.status)
    assertNull(stored?.closedAtMillis)
  }

  private fun alert(
      id: String,
      lostAtMillis: Long = 1L,
      latitude: Double,
      longitude: Double,
      ownerId: String = "owner-1",
  ): Alert =
      Alert(
          id = id,
          petId = "pet-$id",
          ownerId = ownerId,
          lastKnownLocation = Location(latitude = latitude, longitude = longitude),
          lostAtMillis = lostAtMillis,
          petName = "Buddy",
          petSpecies = Species.DOG,
      )
}
