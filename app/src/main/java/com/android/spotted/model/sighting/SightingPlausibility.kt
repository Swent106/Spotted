// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.sighting

import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.distanceKm
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

/** Why a sighting of the pet of an alert can't be true. */
enum class PlausibilityIssue {
  /** Seen more than [FUTURE_TOLERANCE_MILLIS] after now. */
  SEEN_IN_THE_FUTURE,

  /** Seen before the pet was lost. */
  SEEN_BEFORE_LOST,

  /** Farther from where the pet was lost than it could have gone in the time elapsed. */
  TOO_FAR,

  /** Reported by the owner of the alert. */
  OWN_ALERT,
}

/** How far after now a sighting may be, to allow for clocks that are not in sync. */
const val FUTURE_TOLERANCE_MILLIS = 5 * 60 * 1000L

/** A sighting this close to where the pet was lost is always near enough, in kilometres. */
const val MIN_DISTANCE_KM = 2.0

/** Highest average speed of a lost pet, in km/h, by species. */
const val CAT_MAX_SPEED_KMH = 5.0
const val DOG_MAX_SPEED_KMH = 10.0
const val OTHER_MAX_SPEED_KMH = 15.0

/**
 * Lists why [sighting] can't be a sighting of the pet of [alert], given that it is now [nowMillis].
 * The list is empty when the sighting is plausible.
 */
fun checkPlausibility(sighting: Sighting, alert: Alert, nowMillis: Long): List<PlausibilityIssue> {
  val issues = mutableListOf<PlausibilityIssue>()

  if (sighting.seenAtMillis > nowMillis + FUTURE_TOLERANCE_MILLIS) {
    issues.add(PlausibilityIssue.SEEN_IN_THE_FUTURE)
  }
  if (sighting.seenAtMillis < alert.lostAtMillis) {
    issues.add(PlausibilityIssue.SEEN_BEFORE_LOST)
  }

  val elapsedHours =
      (sighting.seenAtMillis - alert.lostAtMillis).milliseconds.toDouble(DurationUnit.HOURS)
  val maxDistanceKm = max(MIN_DISTANCE_KM, maxSpeedKmh(sighting.species) * elapsedHours)
  if (alert.lastKnownLocation.distanceKm(sighting.location) > maxDistanceKm) {
    issues.add(PlausibilityIssue.TOO_FAR)
  }

  if (sighting.reporterId == alert.ownerId) {
    issues.add(PlausibilityIssue.OWN_ALERT)
  }
  return issues
}

private fun maxSpeedKmh(species: Species): Double =
    when (species) {
      Species.CAT -> CAT_MAX_SPEED_KMH
      Species.DOG -> DOG_MAX_SPEED_KMH
      Species.OTHER -> OTHER_MAX_SPEED_KMH
    }
