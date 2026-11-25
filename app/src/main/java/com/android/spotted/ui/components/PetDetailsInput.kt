// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.components

import com.android.spotted.model.Pet.Lifestyle
import com.android.spotted.model.Pet.Microchipped
import com.android.spotted.model.Pet.Neutered
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetSize
import com.android.spotted.model.Pet.Sex

/** The optional pet details filled in by both the Add pet and the Edit pet forms. */
data class PetDetailsInput(
    val sex: Sex = Sex.UNKNOWN,
    val neutered: Neutered = Neutered.UNKNOWN,
    val size: PetSize = PetSize.UNKNOWN,
    val lifestyle: Lifestyle = Lifestyle.UNKNOWN,
    val microchipped: Microchipped = Microchipped.UNKNOWN,
) {
  /** True when at least one detail is given. */
  val isSet: Boolean
    get() = this != PetDetailsInput()

  companion object {
    /** The details already saved on [pet]. */
    fun from(pet: Pet) =
        PetDetailsInput(
            sex = pet.sex,
            neutered = pet.neutered,
            size = pet.size,
            lifestyle = pet.lifestyle,
            microchipped = pet.microchipped,
        )
  }
}
