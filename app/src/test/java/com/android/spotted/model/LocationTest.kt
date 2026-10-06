package com.android.spotted.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationTest {

  @Test
  fun distanceKm_returnsZeroForSameLocation() {
    val location = Location(latitude = 46.5197, longitude = 6.6323)

    assertEquals(0.0, location.distanceKm(location), 0.0)
  }

  @Test
  fun distanceKm_usesGreatCircleDistance() {
    val equator = Location(latitude = 0.0, longitude = 0.0)
    val oneDegreeNorth = Location(latitude = 1.0, longitude = 0.0)

    assertEquals(111.195, equator.distanceKm(oneDegreeNorth), 0.01)
    assertEquals(equator.distanceKm(oneDegreeNorth), oneDegreeNorth.distanceKm(equator), 1e-9)
  }
}
