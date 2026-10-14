// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.location

import com.android.spotted.model.Location
import com.android.spotted.model.location.LocationException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LocationRepositoryFusedTest {

  private lateinit var client: FusedLocationProviderClient
  private var permissionGranted = true
  private lateinit var repository: LocationRepositoryFused

  @Before
  fun setUp() {
    client = mock()
    repository = LocationRepositoryFused(client) { permissionGranted }
  }

  private fun deviceReturns(position: android.location.Location?) {
    whenever(client.getCurrentLocation(any<Int>(), any<CancellationToken>()))
        .thenReturn(Tasks.forResult(position))
  }

  private fun deviceFailsWith(exception: Exception) {
    whenever(client.getCurrentLocation(any<Int>(), any<CancellationToken>()))
        .thenReturn(Tasks.forException(exception))
  }

  @Test
  fun getCurrentLocation_returnsTheDevicePositionAsAppLocation() = runTest {
    val position = mock<android.location.Location>()
    whenever(position.latitude).thenReturn(46.5191)
    whenever(position.longitude).thenReturn(6.5668)
    deviceReturns(position)

    assertEquals(
        Location(latitude = 46.5191, longitude = 6.5668),
        repository.getCurrentLocation().getOrThrow(),
    )
  }

  @Test
  fun getCurrentLocation_withoutPermission_failsWithoutAskingTheDevice() = runTest {
    permissionGranted = false

    val failure = repository.getCurrentLocation().exceptionOrNull()

    assertTrue(failure is LocationException.PermissionDenied)
    verify(client, never()).getCurrentLocation(any<Int>(), any<CancellationToken>())
  }

  @Test
  fun getCurrentLocation_whenNoPositionIsFound_failsAsUnavailable() = runTest {
    deviceReturns(null)

    assertTrue(repository.getCurrentLocation().exceptionOrNull() is LocationException.Unavailable)
  }

  @Test
  fun getCurrentLocation_whenPermissionIsRevoked_failsAsPermissionDenied() = runTest {
    val revoked = SecurityException("Permission revoked")
    deviceFailsWith(revoked)

    val failure = repository.getCurrentLocation().exceptionOrNull()

    assertTrue(failure is LocationException.PermissionDenied)
    assertSame(revoked, failure?.cause)
  }

  @Test
  fun getCurrentLocation_whenTheDeviceFails_failsAsUnavailable() = runTest {
    val error = IllegalStateException("Location services unavailable")
    deviceFailsWith(error)

    val failure = repository.getCurrentLocation().exceptionOrNull()

    assertTrue(failure is LocationException.Unavailable)
    assertSame(error, failure?.cause)
  }

  @Test
  fun getCurrentLocation_whenTheRequestIsCancelled_rethrowsCancellation() = runTest {
    whenever(client.getCurrentLocation(any<Int>(), any<CancellationToken>()))
        .thenReturn(Tasks.forCanceled())

    // A cancelled request must stop the caller, not become a LocationException failure
    val thrown =
        try {
          repository.getCurrentLocation()
          null
        } catch (exception: CancellationException) {
          exception
        }

    assertNotNull(thrown)
  }
}
