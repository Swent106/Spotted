package com.android.spotted.ui.alertdetail

import com.android.spotted.model.alert.Alert

/**
 * State for loading and presenting one alert. It intentionally contains no owner address.
 *
 * @property distanceText "600 m away" from the device position, else from the home; null when
 *   neither is known.
 */
data class AlertDetailUiState(
    val alert: Alert? = null,
    val isLoading: Boolean = false,
    val notFound: Boolean = false,
    val errorMessage: String? = null,
    val distanceText: String? = null,
)
