// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.myalert

import com.android.spotted.model.alert.Alert
import com.android.spotted.model.sighting.LikelyArea
import com.android.spotted.model.sighting.Sighting

data class MyAlertUiState(
    val alert: Alert? = null,
    val sightings: List<Sighting> = emptyList(),
    val likelyArea: LikelyArea? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    /** Navigation flag set when the alert is closed on load or after [MyAlertViewModel.markFound]. */
    val isClosed: Boolean = false,
)
