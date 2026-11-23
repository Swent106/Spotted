// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.Pet

import java.time.Instant
import java.time.ZoneId

/**
 * Age in whole years of a pet born in [birthYear], in the year of [nowMillis] on this device's
 * calendar. Null when the birth year is unknown, or after the current year (a wrong clock or bad
 * data), so that no wrong age is ever shown.
 */
fun ageInYears(birthYear: Int?, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Int? {
  if (birthYear == null) return null
  val currentYear = Instant.ofEpochMilli(nowMillis).atZone(zone).year
  return (currentYear - birthYear).takeIf { it >= 0 }
}
