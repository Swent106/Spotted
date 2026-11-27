// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.alertdetail

import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertStatus

/**
 * State for loading and presenting one alert. It intentionally contains no owner address.
 *
 * @property distanceText "600 m away" from the device position, else from the home; null when
 *   neither is known.
 * @property placeName where the pet was last seen, e.g. "Ouchy, Lausanne"; null while unknown.
 * @property ageYears the pet's age in whole years, from the birth year copied on the alert; null
 *   when unknown.
 * @property isOwnAlert true when the signed-in user published [alert], e.g. opened from a link they
 *   shared.
 * @property shareErrorMessage failure while preparing the alert share text; null otherwise.
 */
data class AlertDetailUiState(
    val alert: Alert? = null,
    val isLoading: Boolean = false,
    val notFound: Boolean = false,
    val errorMessage: String? = null,
    val distanceText: String? = null,
    val placeName: String? = null,
    val ageYears: Int? = null,
    val isOwnAlert: Boolean = false,
    val shareErrorMessage: String? = null,
) {
  /** Only an open alert of someone else: the rules refuse a sighting on your own alert. */
  val canReportSighting: Boolean
    get() = alert?.status == AlertStatus.OPEN && !isOwnAlert
}
