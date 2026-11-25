// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.Pet

import org.junit.Assert.assertEquals
import org.junit.Test

class BreedTest {

  private val mixed = BreedOption(MIXED_BREED_ID, "Mixed")
  private val unknown = BreedOption(UNKNOWN_BREED_ID, "Unknown")
  private val labrador = BreedOption("labrador_retriever", "Labrador Retriever")
  private val jackRussell = BreedOption("jack_russell_terrier", "Jack Russell Terrier")
  private val yorkshire = BreedOption("yorkshire_terrier", "Yorkshire Terrier")
  private val breeds = listOf(mixed, unknown, jackRussell, labrador, yorkshire)

  private fun ids(query: String) = filterBreeds(breeds, query).map { it.id }

  @Test
  fun blankQuery_keepsEveryBreedInOrder() {
    assertEquals(breeds, filterBreeds(breeds, ""))
    assertEquals(breeds, filterBreeds(breeds, "   "))
  }

  @Test
  fun search_ignoresCase() {
    assertEquals(listOf(MIXED_BREED_ID, UNKNOWN_BREED_ID, "labrador_retriever"), ids("LAB"))
    assertEquals(listOf(MIXED_BREED_ID, UNKNOWN_BREED_ID, "labrador_retriever"), ids("lab"))
  }

  @Test
  fun search_matchesInsideTheName_andIgnoresSurroundingSpaces() {
    assertEquals(
        listOf(MIXED_BREED_ID, UNKNOWN_BREED_ID, "jack_russell_terrier", "yorkshire_terrier"),
        ids("  terrier "),
    )
  }

  @Test
  fun noMatch_keepsOnlyMixedAndUnknown() {
    assertEquals(listOf(MIXED_BREED_ID, UNKNOWN_BREED_ID), ids("xyz"))
  }

  @Test
  fun mixedAndUnknown_comeFirst_evenWhenListedLast() {
    val shuffled = listOf(labrador, unknown, jackRussell, mixed)
    assertEquals(
        listOf(UNKNOWN_BREED_ID, MIXED_BREED_ID, "labrador_retriever", "jack_russell_terrier"),
        filterBreeds(shuffled, "").map { it.id },
    )
  }
}
