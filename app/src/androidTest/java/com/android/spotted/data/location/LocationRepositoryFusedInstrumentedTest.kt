// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.location

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.location.LocationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the repository with the real device services, through a context where the location
 * permission is denied. Revoking the real permission would kill the app process the test runs in,
 * and the test must give the same result on a device where it was granted.
 */
@RunWith(AndroidJUnit4::class)
class LocationRepositoryFusedInstrumentedTest {

  @Test
  fun getCurrentLocation_withoutPermission_returnsPermissionDeniedInsteadOfCrashing() = runTest {
    val repository = LocationRepositoryFused(PermissionDeniedContext())

    val failure = repository.getCurrentLocation().exceptionOrNull()

    assertTrue(failure is LocationException.PermissionDenied)
  }

  /** The app's context, except that every permission check is denied. */
  private class PermissionDeniedContext :
      ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
    // The repository uses the application context, so it must also deny its checks
    override fun getApplicationContext(): Context = this

    override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
        PackageManager.PERMISSION_DENIED
  }
}
