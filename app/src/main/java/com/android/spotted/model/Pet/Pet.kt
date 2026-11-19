// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.Pet

enum class Species {
  DOG,
  CAT,
  OTHER,
}

enum class Behavior {
  STRESSED_IN_CROWDS,
  DOESNT_LIKE_BEING_TOUCHED,
  REACTIVE_TO_OTHER_ANIMALS,
  FEARFUL_OF_STRANGERS,
}

// Optional details of a pet (#388). Each has UNKNOWN, the value of pets saved before these existed.

enum class Sex {
  MALE,
  FEMALE,
  UNKNOWN,
}

enum class Neutered {
  YES,
  NO,
  UNKNOWN,
}

enum class Microchipped {
  YES,
  NO,
  UNKNOWN,
}

enum class PetSize {
  SMALL,
  MEDIUM,
  LARGE,
  UNKNOWN,
}

enum class Lifestyle {
  INDOOR_ONLY,
  INDOOR_AND_OUTDOOR,
  OUTDOOR_ONLY,
  UNKNOWN,
}

data class Pet(
    val id: String,
    val ownerId: String,
    val name: String,
    val species: Species,
    val breed: String = "",
    val photoUrl: String? = null, // optional
    val allergies: List<String> = emptyList(),
    val behaviors: Set<Behavior> = emptySet(),
    val note: String = "", // free text
    /** Year of birth, or null if not given. Never in the future. */
    val birthYear: Int? = null,
    /** Breed picked from the list, or null; [breed] stays the free-text fallback. */
    val breedId: String? = null,
    val sex: Sex = Sex.UNKNOWN,
    val neutered: Neutered = Neutered.UNKNOWN,
    val size: PetSize = PetSize.UNKNOWN,
    val lifestyle: Lifestyle = Lifestyle.UNKNOWN,
    val microchipped: Microchipped = Microchipped.UNKNOWN,
)
