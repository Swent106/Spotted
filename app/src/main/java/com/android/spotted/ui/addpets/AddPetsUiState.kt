// Portions of this code were generated with the help of Copilot.
// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.addpets

import com.android.spotted.R
import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.Species
import com.android.spotted.ui.components.PetDetailsInput

sealed interface AddPetsMessage {
  data class Resource(val id: Int, val arguments: List<Any> = emptyList()) : AddPetsMessage
}

data class AddPetsUiState(
    val name: String = "",
    val species: Species? = null,
    val breed: String = "",
    val photoUri: String? = null,
    val description: String = "",
    /** Age typed in the form; kept on screen only for now, not saved ([Pet] has no age yet). */
    val age: String = "",
    val allergies: List<String> = emptyList(),
    val behaviors: Set<Behavior> = emptySet(),
    /** Year of birth, or null when not given. */
    val birthYear: Int? = null,
    /** Why [birthYear] is refused (in the future, or before the allowed range); null when valid. */
    val birthYearError: AddPetsMessage? = null,
    /** Breed picked from the list of [species], or null; [breed] stays the free-text fallback. */
    val breedId: String? = null,
    val details: PetDetailsInput = PetDetailsInput(),
    val addedPets: List<Pet> = emptyList(),
    val isSaving: Boolean = false,
    val isDone: Boolean = false,
    val needsSignIn: Boolean = false,
    val errorMessage: AddPetsMessage? = null,
) {
  /**
   * A name and a species are required to save a pet, and the birth year, if given, must be valid.
   */
  val canSave: Boolean
    get() = name.isNotBlank() && species != null && birthYearError == null && !isSaving

  /** The step can end with an empty form, or with a complete one that is saved first. */
  val canFinish: Boolean
    get() = !isSaving && !isDone && (!hasFormInput || canSave)

  /**
   * Why the started pet can't be saved yet, shown above the buttons so they are never disabled
   * without a reason; null when there is nothing missing.
   */
  val missingField: AddPetsMessage?
    get() =
        when {
          !hasFormInput || isSaving -> null
          name.isBlank() -> AddPetsMessage.Resource(R.string.add_pets_missing_name)
          species == null ->
              AddPetsMessage.Resource(R.string.add_pets_missing_species, listOf(name.trim()))
          else -> null
        }

  /** True when the user has started filling in the current pet. */
  val hasFormInput: Boolean
    get() =
        name.isNotBlank() ||
            species != null ||
            breed.isNotBlank() ||
            photoUri != null ||
            description.isNotBlank() ||
            age.isNotBlank() ||
            allergies.isNotEmpty() ||
            behaviors.isNotEmpty() ||
            birthYear != null ||
            breedId != null ||
            details.isSet
}
