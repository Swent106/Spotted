// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class Location(
    val latitude: Double,
    val longitude: Double,
)

fun Location.distanceKm(other: Location): Double {
  val latitude = Math.toRadians(latitude)
  val otherLatitude = Math.toRadians(other.latitude)
  val latitudeDifference = otherLatitude - latitude
  val longitudeDifference = Math.toRadians(other.longitude - longitude)
  val haversine =
      (sin(latitudeDifference / 2) * sin(latitudeDifference / 2) +
              cos(latitude) *
                  cos(otherLatitude) *
                  sin(longitudeDifference / 2) *
                  sin(longitudeDifference / 2))
          .coerceIn(0.0, 1.0)
  val centralAngle = 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
  return EARTH_RADIUS_KM * centralAngle
}

/**
 * "600 m away" under 1 km (rounded to 10 m), "2.4 km away" under 10 km, "12 km away" beyond. A
 * negative distance counts as 0.
 */
fun formatDistance(km: Double): String {
  val meters = (km.coerceAtLeast(0.0) * 100).roundToInt() * 10
  return when {
    meters < 1000 -> "$meters m away"
    // 9.95 km and above would print "10.0 km"
    km < 9.95 -> "%.1f km away".format(Locale.US, km)
    else -> "${km.roundToInt()} km away"
  }
}

private const val EARTH_RADIUS_KM = 6371.0088
