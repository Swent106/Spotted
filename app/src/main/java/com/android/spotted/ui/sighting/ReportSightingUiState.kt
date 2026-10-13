package com.android.spotted.ui.sighting

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species

data class NearbyAlertOption(
    val alertId: String,
    val petName: String,
    val description: String,
    val photoUrl: String? = null,
)

data class ReportSightingUiState(
    val selectedSpecies: Species? = null,
    val selectedAlertId: String? = null,
    val isNoMatchingAlertSelected: Boolean = false,
    val location: Location? = Location(46.5197, 6.6323), // Default placeholder location (Lausanne)
    val locationAddress: String = "Lausanne, Switzerland",
    val photoUri: String? = null,
    val detectedSpecies: Species? = null,
    val nearbyAlerts: List<NearbyAlertOption> = emptyList(),
    val isOffline: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val submitted: Boolean = false,
) {
  val canSubmit: Boolean
    get() =
        selectedSpecies != null &&
            location != null &&
            (selectedAlertId != null || isNoMatchingAlertSelected) &&
            !isSubmitting &&
            !submitted
}
