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
    val allergies: List<String> = emptyList(),
    val behaviors: Set<Behavior> = emptySet(),
    val addedPets: List<Pet> = emptyList(),
    val isSaving: Boolean = false,
    val isDone: Boolean = false,
    val errorMessage: String? = null,
) {
  val canSave: Boolean
    get() = name.isNotBlank() && species != null && !isSaving

  /** True when the user has started filling in the current pet. */
  val hasFormInput: Boolean
    get() =
        name.isNotBlank() ||
            species != null ||
            breed.isNotBlank() ||
            photoUri != null ||
            description.isNotBlank() ||
            allergies.isNotEmpty() ||
            behaviors.isNotEmpty()
}
