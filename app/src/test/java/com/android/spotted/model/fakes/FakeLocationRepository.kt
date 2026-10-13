package com.android.spotted.model.fakes

import com.android.spotted.model.Location
import com.android.spotted.model.location.LocationRepository

/**
 * [LocationRepository] for ViewModel tests.
 * - [location]: the position returned, [DEFAULT_LOCATION] unless a test changes it.
 * - [failure]: set it (e.g. a `LocationException`) to make [getCurrentLocation] fail with it.
 */
class FakeLocationRepository(var location: Location = DEFAULT_LOCATION) : LocationRepository {
  var failure: Throwable? = null

  override suspend fun getCurrentLocation(): Result<Location> {
    failure?.let {
      return Result.failure(it)
    }
    return Result.success(location)
  }

  companion object {
    /** EPFL, Lausanne. */
    val DEFAULT_LOCATION = Location(latitude = 46.5191, longitude = 6.5668)
  }
}
