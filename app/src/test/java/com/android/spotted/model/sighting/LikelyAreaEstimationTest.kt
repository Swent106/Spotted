// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.sighting

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.distanceKm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LikelyAreaEstimationTest {

  @Test
  fun noConfirmedSighting_returnsNull() {
    assertNull(estimateLikelyArea(emptyList(), Species.DOG, NOW))
    assertNull(
        estimateLikelyArea(
            listOf(
                sighting(LAUSANNE, status = SightingStatus.PENDING),
                sighting(LAUSANNE, status = SightingStatus.DISMISSED),
            ),
            Species.DOG,
            NOW,
        )
    )
  }

  @Test
  fun oneSighting_isTheCentre_withTheSpeciesBaseRadius() {
    val area = estimateLikelyArea(listOf(sighting(LAUSANNE)), Species.CAT, NOW)!!

    assertEquals(LAUSANNE, area.center)
    assertEquals(200.0, area.radiusMeters, DELTA_METERS)
  }

  @Test
  fun pendingAndDismissedSightings_areIgnored() {
    val confirmed = sighting(LAUSANNE)
    val pending = sighting(Location(46.60, 6.70), status = SightingStatus.PENDING)
    val dismissed = sighting(Location(46.40, 6.50), status = SightingStatus.DISMISSED)

    assertEquals(
        estimateLikelyArea(listOf(confirmed), Species.CAT, NOW),
        estimateLikelyArea(listOf(confirmed, pending, dismissed), Species.CAT, NOW),
    )
  }

  @Test
  fun recentSightings_pullTheCentreMoreThanOldOnes() {
    // 12 hours older is two half-lives: the old sighting weighs 1/4, the recent one 1
    val old = sighting(Location(46.50, 6.60), seenAtMillis = NOW - 12 * HOUR)
    val recent = sighting(Location(46.52, 6.60), seenAtMillis = NOW)

    val center = estimateLikelyArea(listOf(old, recent), Species.DOG, NOW)!!.center

    assertEquals((46.50 * 0.25 + 46.52 * 1) / 1.25, center.latitude, DELTA_DEGREES)
    assertEquals(6.60, center.longitude, DELTA_DEGREES)
    assertTrue(center.distanceKm(recent.location) < center.distanceKm(old.location))
  }

  @Test
  fun radius_growsWithTimeSinceTheLastSighting_upToTheCap() {
    val lastSeen = listOf(sighting(LAUSANNE, seenAtMillis = NOW))

    fun radiusAfter(hours: Long) =
        estimateLikelyArea(lastSeen, Species.DOG, NOW + hours * HOUR)!!.radiusMeters

    // Dog: 500 m, plus 300 m per hour, at most 5 km
    assertEquals(500.0, radiusAfter(0), DELTA_METERS)
    assertEquals(1_100.0, radiusAfter(2), DELTA_METERS)
    assertEquals(5_000.0, radiusAfter(100), DELTA_METERS)
  }

  @Test
  fun cats_stayCloserThanDogs() {
    val lastSeen = listOf(sighting(LAUSANNE, seenAtMillis = NOW))

    fun radius(species: Species, hours: Long) =
        estimateLikelyArea(lastSeen, species, NOW + hours * HOUR)!!.radiusMeters

    assertEquals(200.0, radius(Species.CAT, 0), DELTA_METERS)
    assertEquals(500.0, radius(Species.DOG, 0), DELTA_METERS)
    // The caps: 1 km for a cat, 5 km for a dog
    assertEquals(1_000.0, radius(Species.CAT, 100), DELTA_METERS)
    assertEquals(5_000.0, radius(Species.DOG, 100), DELTA_METERS)
  }

  @Test
  fun sightingsFurtherApartThanTheRadius_areAllInsideTheArea() {
    // About 3.3 km apart at the same time: the centre is their plain average, and a cat's 200 m
    // (or even its 1 km cap) would contain neither
    val north = sighting(Location(46.53, 6.60))
    val south = sighting(Location(46.50, 6.60))

    val area = estimateLikelyArea(listOf(north, south), Species.CAT, NOW)!!

    assertEquals(46.515, area.center.latitude, DELTA_DEGREES)
    assertEquals(area.center.distanceKm(north.location) * 1000, area.radiusMeters, DELTA_METERS)
    assertTrue(area.radiusMeters >= area.center.distanceKm(south.location) * 1000 - DELTA_METERS)
    assertTrue(area.radiusMeters > 1_000.0)
  }

  @Test
  fun sightingDatedInTheFuture_countsAsSeenNow() {
    // A phone with a wrong clock: the radius must not shrink below the base
    val fromTheFuture = sighting(LAUSANNE, seenAtMillis = NOW + 2 * HOUR)

    val area = estimateLikelyArea(listOf(fromTheFuture), Species.DOG, NOW)!!

    assertEquals(500.0, area.radiusMeters, DELTA_METERS)
  }

  @Test
  fun sightingsYearsOld_doNotBreakTheCentre() {
    // Ten years is so many half-lives that its weight rounds to 0; the centre must stay a number
    val ancient = sighting(Location(46.40, 6.50), seenAtMillis = NOW - 10 * 365 * 24 * HOUR)
    val recent = sighting(LAUSANNE, seenAtMillis = NOW)

    val center = estimateLikelyArea(listOf(ancient, recent), Species.CAT, NOW)!!.center

    assertEquals(LAUSANNE.latitude, center.latitude, DELTA_DEGREES)
    assertEquals(LAUSANNE.longitude, center.longitude, DELTA_DEGREES)
  }

  private fun sighting(
      location: Location,
      seenAtMillis: Long = NOW,
      status: SightingStatus = SightingStatus.CONFIRMED,
  ) =
      Sighting(
          id = "sighting-${location.latitude}-$seenAtMillis",
          reporterId = "passerby-1",
          location = location,
          seenAtMillis = seenAtMillis,
          species = Species.CAT,
          alertId = "alert-1",
          status = status,
      )

  private companion object {
    val LAUSANNE = Location(46.5197, 6.6323)
    const val NOW = 1_800_000_000_000L
    const val HOUR = 60L * 60 * 1000
    const val DELTA_METERS = 0.001
    const val DELTA_DEGREES = 1e-9
  }
}
