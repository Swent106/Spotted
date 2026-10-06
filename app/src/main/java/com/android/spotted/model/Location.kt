package com.android.spotted.model

import kotlin.math.atan2
import kotlin.math.cos
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

private const val EARTH_RADIUS_KM = 6371.0088
