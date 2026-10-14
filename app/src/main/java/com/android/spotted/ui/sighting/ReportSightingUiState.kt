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
    val selectedAlertId: String? = null,
    val isNoMatchingAlertSelected: Boolean = false,
    val location: Location? = null,
    val locationAddress: String = "Location on map",
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
        (selectedAlertId != null || isNoMatchingAlertSelected) &&
            location != null &&
            !isSubmitting &&
            !submitted
}
