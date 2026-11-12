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
 * @property openAlertIdByPetId id of the open alert of each pet already reported missing. Those
 *   pets can't be selected.
 * @property alreadyMissingPetId pet already missing that the owner tapped: the screen offers to
 *   open its alert. Null when no such offer is shown.
 * @property openAlertId alert to open in My alert; the screen then navigates there.
 */
data class ReportMissingUiState(
    val pets: List<Pet> = emptyList(),
    val selectedPetId: String? = null,
    val location: Location? = null,
    val isLoadingPets: Boolean = false,
    val isPublishing: Boolean = false,
    val errorMessage: String? = null,
    val published: Boolean = false,
    val openAlertIdByPetId: Map<String, String> = emptyMap(),
    val alreadyMissingPetId: String? = null,
    val openAlertId: String? = null,
) {
  /** The pet the owner chose, or null if none is chosen. A pet already missing is never chosen. */
  val selectedPet: Pet?
    get() = pets.firstOrNull { it.id == selectedPetId && !isAlreadyMissing(it) }

  /** The pet already missing whose alert the screen offers to open, or null. */
  val alreadyMissingPet: Pet?
    get() = pets.firstOrNull { it.id == alreadyMissingPetId }

  /** True if the owner has pets and every one of them is already reported missing. */
  val allPetsMissing: Boolean
    get() = pets.isNotEmpty() && pets.all(::isAlreadyMissing)

  /** True if [pet] already has an open alert. */
  fun isAlreadyMissing(pet: Pet): Boolean = pet.id in openAlertIdByPetId

  /** A pet and a location are required, and an alert can only be published once. */
  val canPublish: Boolean
    get() = selectedPet != null && location != null && !isPublishing && !published
}
