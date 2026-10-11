// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.report

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Pet

/**
 * State of the "Report a pet missing" screen.
 *
 * @property pets the signed-in owner's pets, one of which is reported missing.
 * @property selectedPetId id of the pet the owner chose, or null if none is chosen yet.
 * @property location last known location of the pet, chosen on the map picker.
 * @property isLoadingPets true while the owner's pets are being loaded.
 * @property isPublishing true while the alert is being published.
 * @property errorMessage message to show to the user, or null if there is none.
 * @property published true once the alert is published; the screen then navigates away.
 */
data class ReportMissingUiState(
    val pets: List<Pet> = emptyList(),
    val selectedPetId: String? = null,
    val location: Location? = null,
    val isLoadingPets: Boolean = false,
    val isPublishing: Boolean = false,
    val errorMessage: String? = null,
    val published: Boolean = false,
) {
  /** The pet the owner chose, or null if none is chosen. */
  val selectedPet: Pet?
    get() = pets.firstOrNull { it.id == selectedPetId }

  /** A pet and a location are required, and an alert can only be published once. */
  val canPublish: Boolean
    get() = selectedPet != null && location != null && !isPublishing && !published
}
