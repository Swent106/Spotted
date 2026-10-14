package com.android.spotted.model.location

/**
 * Why [LocationRepository.getCurrentLocation] failed, so ViewModels can react differently (for
 * example, let the user place the pin by hand when the permission is denied).
 */
sealed class LocationException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

  /** The app is not allowed to read the device's location. */
  class PermissionDenied(cause: Throwable? = null) :
      LocationException("Location permission is not granted", cause)

  /** The position could not be found (location turned off, no fix, ...). */
  class Unavailable(cause: Throwable? = null) :
      LocationException("Current location is unavailable", cause)
}
