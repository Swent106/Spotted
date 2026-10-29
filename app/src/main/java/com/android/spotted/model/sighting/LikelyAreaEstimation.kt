// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.sighting

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.distanceKm
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Estimates where a lost pet most likely is, from the sightings its owner confirmed.
 *
 * The formula is simple on purpose, so it can be explained and tuned:
 * 1. Only [SightingStatus.CONFIRMED] sightings count. With none, there is nothing to estimate.
 * 2. Centre: the average of the sightings' positions, each weighted by how recent it is. A
 *    sighting's weight halves for every [RECENCY_HALF_LIFE_MILLIS] (6 hours) it is older than the
 *    newest one, so the pet's latest positions pull the centre the most.
 * 3. Radius: how far this species usually roams, plus how far it may have gone since it was last
 *    seen, capped because a lost pet does not keep walking in a straight line (see [RoamingRange]).
 * 4. The area always contains every confirmed sighting: if they are further apart than the radius
 *    above, the radius grows to the farthest one. That part is not capped, since the sightings are
 *    real observations.
 *
 * Limits:
 * - The roaming constants are rough assumptions (cats usually hide close to where they got lost,
 *   dogs travel further), not measurements. They should be tuned with real data.
 * - Averaging latitudes and longitudes is only accurate over a few kilometres, the scale of a lost
 *   pet, and does not work across the 180th meridian.
 * - The pet is assumed to move the same way in every direction: roads, rivers and its home are
 *   ignored.
 * - An old confirmed sighting far away keeps the area large until the owner dismisses it.
 *
 * @param confirmed the alert's sightings; those that are not confirmed are ignored.
 * @param species species of the lost pet, which sets how far it may roam.
 * @param nowMillis the current time (epoch millis). A sighting dated after it (a phone with a wrong
 *   clock) counts as seen now.
 * @return the likely area, or null if no sighting is confirmed.
 */
fun estimateLikelyArea(confirmed: List<Sighting>, species: Species, nowMillis: Long): LikelyArea? {
  val sightings = confirmed.filter { it.status == SightingStatus.CONFIRMED }
  if (sightings.isEmpty()) return null

  // Weights are relative to the newest sighting rather than to now: the centre is the same (a
  // common factor cancels out), and the newest weight is always 1, so very old sightings cannot
  // make every weight round down to 0
  val newestSeenAt = sightings.maxOf { it.seenAtMillis }
  val weights = sightings.map {
    0.5.pow((newestSeenAt - it.seenAtMillis) / RECENCY_HALF_LIFE_MILLIS)
  }
  val weighted = sightings.map { it.location }.zip(weights)
  val totalWeight = weights.sum()
  val center =
      Location(
          latitude =
              weighted.sumOf { (location, weight) -> location.latitude * weight } / totalWeight,
          longitude =
              weighted.sumOf { (location, weight) -> location.longitude * weight } / totalWeight,
      )

  val range = roamingRange(species)
  val hoursSinceLastSeen = max(0L, nowMillis - newestSeenAt).toDouble() / MILLIS_PER_HOUR
  val roamingMeters =
      min(range.baseMeters + range.metersPerHour * hoursSinceLastSeen, range.maxMeters)
  val farthestSightingMeters =
      sightings.maxOf { center.distanceKm(it.location) } * METERS_PER_KILOMETER

  return LikelyArea(center = center, radiusMeters = max(roamingMeters, farthestSightingMeters))
}

/**
 * How far a lost pet of one species may be from where it was last seen.
 *
 * @property baseMeters Radius right after the sighting: how far it usually wanders.
 * @property metersPerHour How much the radius grows for every hour without a new sighting.
 * @property maxMeters Largest radius the time growth can reach.
 */
internal data class RoamingRange(
    val baseMeters: Double,
    val metersPerHour: Double,
    val maxMeters: Double,
)

/** Rough assumptions, to tune with real data: cats hide nearby, dogs travel further. */
internal fun roamingRange(species: Species): RoamingRange =
    when (species) {
      Species.CAT -> RoamingRange(baseMeters = 200.0, metersPerHour = 50.0, maxMeters = 1_000.0)
      Species.DOG -> RoamingRange(baseMeters = 500.0, metersPerHour = 300.0, maxMeters = 5_000.0)
      Species.OTHER -> RoamingRange(baseMeters = 300.0, metersPerHour = 150.0, maxMeters = 3_000.0)
    }

/** A sighting's weight in the centre halves for every 6 hours it is older than the newest one. */
internal const val RECENCY_HALF_LIFE_MILLIS = 6.0 * 60 * 60 * 1000

private const val MILLIS_PER_HOUR = 60.0 * 60 * 1000
private const val METERS_PER_KILOMETER = 1000.0
