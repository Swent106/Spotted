// Portions of this code were generated with the help of Copilot.
// Portions of this code were generated with the help of Claude (Anthropic).
// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.sighting

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species

data class NearbyAlertOption(
    val alertId: String,
    val petName: String,
    val description: String,
    val photoUrl: String? = null,
    val species: Species? = null,
)

/** Where the check that the sighting photo shows an animal is at. */
enum class PhotoCheck {
  NONE,
  CHECKING,
  ANIMAL,
  NO_ANIMAL,
}

data class ReportSightingUiState(
    val selectedSpecies: Species? = null,
    val selectedAlertId: String? = null,
    val isNoMatchingAlertSelected: Boolean = false,
    val location: Location? = null,
    val direction: Int? = null,
    val locationAddress: String = "Tap to set location",
    val photoUri: String? = null,
    val headingDegrees: Int? = null,
    val detectedSpecies: Species? = null,
    val nearbyAlerts: List<NearbyAlertOption> = emptyList(),
    val isOffline: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val submitted: Boolean = false,
    val photoCheck: PhotoCheck = PhotoCheck.NONE,
) {
  val canSubmit: Boolean
    get() =
        selectedSpecies != null &&
            location != null &&
            (selectedAlertId != null || isNoMatchingAlertSelected) &&
            photoCheck != PhotoCheck.CHECKING &&
            photoCheck != PhotoCheck.NO_ANIMAL &&
            !isSubmitting &&
            !submitted
}
