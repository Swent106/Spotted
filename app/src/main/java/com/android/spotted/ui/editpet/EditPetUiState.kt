// Portions of this code were generated with the help of Claude (Anthropic).
// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.editpet

import androidx.annotation.StringRes
import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.Species
import com.android.spotted.ui.components.PetDetailsInput

data class EditPetUiState(
    val name: String = "",
    val species: Species? = null,
    val breed: String = "",
    val breedId: String? = null,
    val photoUri: String? = null,
    val description: String = "",
    val birthYear: Int? = null,
    val details: PetDetailsInput = PetDetailsInput(),
    val allergies: List<String> = emptyList(),
    val behaviors: Set<Behavior> = emptySet(),
    val isLoading: Boolean = true,
    /** The pet could not be loaded: there is nothing to edit. */
    val loadFailed: Boolean = false,
    val saving: Boolean = false,
    /** The changes are saved; the View leaves the form. */
    val saved: Boolean = false,
    val deleted: Boolean = false,
    @StringRes val errorMessage: Int? = null,
) {
  val canSave: Boolean
    get() = !isLoading && !loadFailed && !saving && !deleted && name.isNotBlank() && species != null
}
