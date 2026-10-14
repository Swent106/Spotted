package com.android.spotted.model.location

import com.android.spotted.model.Location
import com.android.spotted.model.fakes.FakeLocationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Checks that the fake follows the [LocationRepository] contract the ViewModel tests rely on. */
class FakeLocationRepositoryTest {
  private val repository = FakeLocationRepository()

  @Test
  fun getCurrentLocation_byDefault_returnsDefaultLocation() = runTest {
    assertEquals(
        FakeLocationRepository.DEFAULT_LOCATION,
        repository.getCurrentLocation().getOrThrow(),
    )
  }

  @Test
  fun getCurrentLocation_returnsLocationSetByTest() = runTest {
    val bern = Location(latitude = 46.948, longitude = 7.4474)
    repository.location = bern

    assertEquals(bern, repository.getCurrentLocation().getOrThrow())
  }

  @Test
  fun getCurrentLocation_whenPermissionDenied_returnsThatFailure() = runTest {
    val failure = LocationException.PermissionDenied()
    repository.failure = failure

    assertSame(failure, repository.getCurrentLocation().exceptionOrNull())
  }

  @Test
  fun getCurrentLocation_whenUnavailable_returnsThatFailure() = runTest {
    val failure = LocationException.Unavailable()
    repository.failure = failure

    assertSame(failure, repository.getCurrentLocation().exceptionOrNull())
  }

  @Test
  fun getCurrentLocation_afterFailureCleared_returnsLocationAgain() = runTest {
    repository.failure = LocationException.Unavailable()
    repository.failure = null

    assertEquals(
        FakeLocationRepository.DEFAULT_LOCATION,
        repository.getCurrentLocation().getOrThrow(),
    )
  }

  @Test
  fun locationExceptions_haveReadableMessages() {
    assertEquals(
        "Location permission is not granted",
        LocationException.PermissionDenied().message,
    )
    assertEquals("Current location is unavailable", LocationException.Unavailable().message)
  }
}
