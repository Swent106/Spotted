// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.photo

import com.android.spotted.model.Pet.Species
import com.android.spotted.model.photo.AnimalDetection
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests of [toDetection], which turns ML Kit's labels into an [AnimalDetection]. */
class AnimalDetectorMlKitTest {

  @Test
  fun dogLabel_isADog() {
    assertEquals(DOG, toDetection(listOf("Dog" to 0.9f, "Grass" to 0.8f)))
  }

  @Test
  fun catLabel_isACat() {
    assertEquals(CAT, toDetection(listOf("Cat" to 0.85f)))
  }

  @Test
  fun anotherAnimal_isOther() {
    assertEquals(
        AnimalDetection(isAnimal = true, species = Species.OTHER),
        toDetection(listOf("Bird" to 0.7f)),
    )
  }

  @Test
  fun petWithADog_isADog() {
    // "Pet" alone says nothing of the species: the dog label gives it
    assertEquals(DOG, toDetection(listOf("Pet" to 0.95f, "Dog" to 0.7f)))
  }

  @Test
  fun dogAndCat_keepTheMostConfidentOne() {
    assertEquals(CAT, toDetection(listOf("Dog" to 0.65f, "Cat" to 0.9f)))
    assertEquals(DOG, toDetection(listOf("Dog" to 0.9f, "Cat" to 0.65f)))
  }

  @Test
  fun noAnimalLabel_isNoAnimal() {
    assertEquals(NO_ANIMAL, toDetection(listOf("Sky" to 0.9f, "Mountain" to 0.8f)))
  }

  @Test
  fun animalUnderTheThreshold_isIgnored() {
    assertEquals(NO_ANIMAL, toDetection(listOf("Dog" to MIN_CONFIDENCE - 0.01f)))
  }

  @Test
  fun animalAtTheThreshold_counts() {
    assertEquals(DOG, toDetection(listOf("Dog" to MIN_CONFIDENCE)))
  }

  @Test
  fun dogUnderTheThresholdWithACat_isACat() {
    assertEquals(CAT, toDetection(listOf("Dog" to 0.5f, "Cat" to 0.7f)))
  }

  @Test
  fun noLabel_isNoAnimal() {
    assertEquals(NO_ANIMAL, toDetection(emptyList()))
  }

  @Test
  fun labels_matchWhateverTheCase() {
    assertEquals(DOG, toDetection(listOf("DOG" to 0.9f)))
    assertEquals(CAT, toDetection(listOf("cat" to 0.9f)))
  }

  private companion object {
    val DOG = AnimalDetection(isAnimal = true, species = Species.DOG)
    val CAT = AnimalDetection(isAnimal = true, species = Species.CAT)
    val NO_ANIMAL = AnimalDetection(isAnimal = false, species = null)
  }
}
