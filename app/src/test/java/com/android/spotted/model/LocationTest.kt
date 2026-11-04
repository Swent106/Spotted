// Portions of this code were generated with the help of Claude (Anthropic).
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

  @Test
  fun formatDistance_underOneKm_isInMetersRoundedToTen() {
    assertEquals("0 m away", formatDistance(0.0))
    assertEquals("600 m away", formatDistance(0.6))
    assertEquals("610 m away", formatDistance(0.6149))
    assertEquals("990 m away", formatDistance(0.994))
  }

  @Test
  fun formatDistance_roundingUpToOneKm_isInKm() {
    assertEquals("1.0 km away", formatDistance(0.996))
  }

  @Test
  fun formatDistance_underTenKm_hasOneDecimal() {
    assertEquals("1.0 km away", formatDistance(1.0))
    assertEquals("2.4 km away", formatDistance(2.44))
    assertEquals("9.9 km away", formatDistance(9.94))
  }

  @Test
  fun formatDistance_tenKmOrMore_isRoundedToTheKm() {
    assertEquals("10 km away", formatDistance(9.96))
    assertEquals("10 km away", formatDistance(10.0))
    assertEquals("13 km away", formatDistance(12.6))
  }

  @Test
  fun formatDistance_negative_countsAsZero() {
    assertEquals("0 m away", formatDistance(-1.0))
  }
}
