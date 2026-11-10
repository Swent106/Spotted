// Portions of this code were generated with the help of Copilot.
package com.android.spotted.model.sighting

/** Returns the nearest 8-point compass label for [degrees], normalizing values outside 0..359. */
fun compassLabel(degrees: Int): String {
  val normalized = ((degrees % 360) + 360) % 360
  val index = ((normalized + 22) / 45) % COMPASS_LABELS.size
  return COMPASS_LABELS[index]
}

private val COMPASS_LABELS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
