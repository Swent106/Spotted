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

data class Pet(
    val id: String,
    val ownerId: String,
    val name: String,
    val species: Species,
    val breed: String = "",
    val photoUrl: String? = null, // optionnel
    val allergies: List<String> = emptyList(),
    val behaviors: Set<Behavior> = emptySet(),
    val note: String = "", // texte libre
)
