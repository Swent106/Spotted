package com.android.spotted.ui.addpets

import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.Species

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
    val addedPets: List<Pet> = emptyList(),
    val isSaving: Boolean = false,
    val isDone: Boolean = false,
    val needsSignIn: Boolean = false,
    val errorMessage: String? = null,
) {
  /** A name and a species are required to save a pet. */
  val canSave: Boolean
    get() = name.isNotBlank() && species != null && !isSaving

  /** The step can end with an empty form, or with a complete one that is saved first. */
  val canFinish: Boolean
    get() = !isSaving && !isDone && (!hasFormInput || canSave)

  /**
   * Why the started pet can't be saved yet, shown above the buttons so they are never disabled
   * without a reason; null when there is nothing missing.
   */
  val missingField: String?
    get() =
        when {
          !hasFormInput || isSaving -> null
          name.isBlank() -> "Add your pet's name to save it"
          species == null -> "Choose an animal to save ${name.trim()}"
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
            behaviors.isNotEmpty()
}
