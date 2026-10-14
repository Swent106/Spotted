// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.location

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.location.LocationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the repository with the real device services. The location permission is never granted to
 * the test app, so the result does not depend on a GPS fix.
 */
@RunWith(AndroidJUnit4::class)
class LocationRepositoryFusedInstrumentedTest {

  @Test
  fun getCurrentLocation_withoutPermission_returnsPermissionDeniedInsteadOfCrashing() = runTest {
    val repository = LocationRepositoryFused(ApplicationProvider.getApplicationContext())

    val failure = repository.getCurrentLocation().exceptionOrNull()

    assertTrue(failure is LocationException.PermissionDenied)
  }
}
