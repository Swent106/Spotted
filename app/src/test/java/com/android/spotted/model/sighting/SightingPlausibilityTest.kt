// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.sighting

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SightingPlausibilityTest {

  @Test
  fun plausibleSighting_hasNoIssue() {
    val sighting = sighting(seenAtMillis = LOST_AT + HOUR, location = northOf(LOST_AT_PLACE, 1.0))

    assertTrue(checkPlausibility(sighting, ALERT, NOW).isEmpty())
  }

  // ===================== Seen in the future =====================

  @Test
  fun seenMoreThanFiveMinutesAhead_isInTheFuture() {
    val sighting = sighting(seenAtMillis = NOW + FUTURE_TOLERANCE_MILLIS + 1)

    assertEquals(
        listOf(PlausibilityIssue.SEEN_IN_THE_FUTURE),
        checkPlausibility(sighting, ALERT, NOW),
    )
  }

  @Test
  fun seenExactlyFiveMinutesAhead_isAccepted() {
    val sighting = sighting(seenAtMillis = NOW + FUTURE_TOLERANCE_MILLIS)

    assertTrue(checkPlausibility(sighting, ALERT, NOW).isEmpty())
  }

  // ===================== Seen before lost =====================

  @Test
  fun seenBeforeThePetWasLost_isRefused() {
    val sighting = sighting(seenAtMillis = LOST_AT - 1)

    assertEquals(
        listOf(PlausibilityIssue.SEEN_BEFORE_LOST),
        checkPlausibility(sighting, ALERT, NOW),
    )
  }

  @Test
  fun seenWhenThePetWasLost_isAccepted() {
    val sighting = sighting(seenAtMillis = LOST_AT)

    assertTrue(checkPlausibility(sighting, ALERT, NOW).isEmpty())
  }

  // ===================== Too far =====================

  // The 2 km boundary is tested one metre on each side: a distance computed on a sphere is never
  // exactly 2 km, so "exactly" would only test floating-point rounding.

  @Test
  fun oneMetreOverTwoKilometresRightAfterTheLoss_isTooFar() {
    val sighting =
        sighting(seenAtMillis = LOST_AT, location = northOf(LOST_AT_PLACE, MIN_DISTANCE_KM + METRE))

    assertEquals(listOf(PlausibilityIssue.TOO_FAR), checkPlausibility(sighting, ALERT, NOW))
  }

  @Test
  fun oneMetreUnderTwoKilometresRightAfterTheLoss_isAccepted() {
    val sighting =
        sighting(seenAtMillis = LOST_AT, location = northOf(LOST_AT_PLACE, MIN_DISTANCE_KM - METRE))

    assertTrue(checkPlausibility(sighting, ALERT, NOW).isEmpty())
  }

  @Test
  fun maxDistanceGrowsWithTheTimeElapsed_atTheSpeedOfTheSpecies() {
    // A dog may go 10 km/h: after 3 hours, 29 km is fine but 31 km is too far
    val near = sighting(seenAtMillis = LOST_AT + 3 * HOUR, location = northOf(LOST_AT_PLACE, 29.0))
    val far = near.copy(location = northOf(LOST_AT_PLACE, 31.0))

    assertTrue(checkPlausibility(near, ALERT, NOW).isEmpty())
    assertEquals(listOf(PlausibilityIssue.TOO_FAR), checkPlausibility(far, ALERT, NOW))
  }

  @Test
  fun maxSpeed_dependsOnTheSpecies() {
    // After 2 hours, 15 km is too far for a cat (10 km), fine for a dog (20 km) and others (30 km)
    val cat =
        sighting(
            seenAtMillis = LOST_AT + 2 * HOUR,
            location = northOf(LOST_AT_PLACE, 15.0),
            species = Species.CAT,
        )

    assertEquals(listOf(PlausibilityIssue.TOO_FAR), checkPlausibility(cat, ALERT, NOW))
    assertTrue(checkPlausibility(cat.copy(species = Species.DOG), ALERT, NOW).isEmpty())
    assertTrue(checkPlausibility(cat.copy(species = Species.OTHER), ALERT, NOW).isEmpty())
  }

  // ===================== Own alert =====================

  @Test
  fun reportedByTheOwnerOfTheAlert_isRefused() {
    val sighting = sighting(reporterId = OWNER)

    assertEquals(listOf(PlausibilityIssue.OWN_ALERT), checkPlausibility(sighting, ALERT, NOW))
  }

  @Test
  fun severalIssues_areAllListed() {
    val sighting =
        sighting(
            reporterId = OWNER,
            seenAtMillis = LOST_AT - 1,
            location = northOf(LOST_AT_PLACE, 50.0),
        )

    assertEquals(
        listOf(
            PlausibilityIssue.SEEN_BEFORE_LOST,
            PlausibilityIssue.TOO_FAR,
            PlausibilityIssue.OWN_ALERT,
        ),
        checkPlausibility(sighting, ALERT, NOW),
    )
  }

  private fun sighting(
      reporterId: String = PASSERBY,
      seenAtMillis: Long = LOST_AT + HOUR,
      location: Location = LOST_AT_PLACE,
      species: Species = Species.DOG,
  ) =
      Sighting(
          id = "sighting",
          reporterId = reporterId,
          location = location,
          seenAtMillis = seenAtMillis,
          species = species,
          alertId = ALERT.id,
      )

  /** The place [km] kilometres north of [from], along its meridian. */
  private fun northOf(from: Location, km: Double) =
      from.copy(latitude = from.latitude + Math.toDegrees(km / EARTH_RADIUS_KM))

  private companion object {
    const val HOUR = 3_600_000L
    const val METRE = 0.001
    const val LOST_AT = 1_000_000_000L
    const val NOW = LOST_AT + 24 * HOUR
    const val OWNER = "owner"
    const val PASSERBY = "passerby"

    /** Same value as in Location.kt, so [northOf] gives exact distances. */
    const val EARTH_RADIUS_KM = 6371.0088

    val LOST_AT_PLACE = Location(46.52, 6.63)
    val ALERT =
        Alert(
            id = "alert",
            petId = "pet",
            ownerId = OWNER,
            lastKnownLocation = LOST_AT_PLACE,
            lostAtMillis = LOST_AT,
            petName = "Rex",
            petSpecies = Species.DOG,
        )
  }
}
