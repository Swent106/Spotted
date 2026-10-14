// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.android.spotted.model.Location
import com.android.spotted.model.location.LocationException
import com.android.spotted.model.location.LocationRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * [LocationRepository] backed by Google Play services' fused location provider.
 *
 * Failures are returned as [Result.failure] with a [LocationException], never thrown: a missing
 * permission gives [LocationException.PermissionDenied], and no position (location turned off, no
 * fix, or any other error from the provider) gives [LocationException.Unavailable].
 *
 * @property client asks the device for its position.
 * @property hasPermission true when the app may read the location (precise or approximate).
 */
class LocationRepositoryFused(
    private val client: FusedLocationProviderClient,
    private val hasPermission: () -> Boolean,
) : LocationRepository {

  /** Uses the device's location provider and checks the permissions with [context]. */
  constructor(
      context: Context
  ) : this(
      client = LocationServices.getFusedLocationProviderClient(context),
      hasPermission = { hasLocationPermission(context) },
  )

  override suspend fun getCurrentLocation(): Result<Location> {
    if (!hasPermission()) return Result.failure(LocationException.PermissionDenied())
    // Cancels the request if the caller is cancelled (e.g. the screen is closed)
    val cancellation = CancellationTokenSource()
    return try {
      // await() suspends without blocking, so this is main-safe
      val position =
          client
              .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
              .await(cancellation)
      // No position when location is turned off or no fix was found in time
      if (position == null) {
        Result.failure(LocationException.Unavailable())
      } else {
        Result.success(Location(latitude = position.latitude, longitude = position.longitude))
      }
    } catch (exception: CancellationException) {
      // Rethrown, not turned into a failure: the caller was cancelled (e.g. the screen was closed),
      // so the coroutine must stop instead of going on with a result nobody waits for
      throw exception
    } catch (exception: SecurityException) {
      // The permission was revoked between the check and the request
      Result.failure(LocationException.PermissionDenied(exception))
    } catch (exception: Exception) {
      Result.failure(LocationException.Unavailable(exception))
    }
  }

  private companion object {
    fun hasLocationPermission(context: Context): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any {
              ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
  }
}
