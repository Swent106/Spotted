package com.android.spotted.model.location

import com.android.spotted.model.Location

/**
 * Gives the device's current position.
 *
 * Contract shared by every implementation (GPS and fakes):
 * - errors are returned as [Result.failure], never thrown to the caller;
 * - a missing location permission fails with [LocationException.PermissionDenied];
 * - no position available (location turned off, no fix) fails with [LocationException.Unavailable];
 * - implementations are main-safe (blocking I/O runs off the main thread).
 */
interface LocationRepository {

  /** Returns the device's current position. */
  suspend fun getCurrentLocation(): Result<Location>
}
