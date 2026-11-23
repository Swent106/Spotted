// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.Pet

/** Id of the "Mixed" breed, present in every breed list. */
const val MIXED_BREED_ID = "mixed"

/** Id of the "Unknown" breed, present in every breed list. */
const val UNKNOWN_BREED_ID = "unknown"

/**
 * Ids of the breeds always shown first in a breed list, whatever the search: "Mixed", "Unknown".
 */
val PINNED_BREED_IDS: Set<String> = setOf(MIXED_BREED_ID, UNKNOWN_BREED_ID)

/**
 * One breed the user can pick. [id] is stable and is what gets stored; [name] is only shown and may
 * change or be translated.
 */
data class BreedOption(val id: String, val name: String)

/**
 * Returns the breeds to show for [query]: "Mixed" and "Unknown" always come first, then the other
 * breeds whose name contains [query], ignoring case and surrounding spaces. A blank query keeps
 * every breed. The order of [breeds] is kept.
 */
fun filterBreeds(breeds: List<BreedOption>, query: String): List<BreedOption> {
  val pinned = breeds.filter { it.id in PINNED_BREED_IDS }
  val others = breeds.filter { it.id !in PINNED_BREED_IDS }

  val q = query.trim()
  val matches =
      if (q.isEmpty()) others else others.filter { it.name.contains(q, ignoreCase = true) }

  return pinned + matches
}
