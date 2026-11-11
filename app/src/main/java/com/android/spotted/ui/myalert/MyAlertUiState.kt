// Portions of this code were generated with the help of Copilot.
// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.myalert

import com.android.spotted.model.alert.Alert
import com.android.spotted.model.sighting.LikelyArea
import com.android.spotted.model.sighting.Sighting
import com.android.spotted.model.sighting.SightingStatus

/**
 * State of the owner's "My alert" screen: one of their alerts, the sightings reported on it, and
 * where the pet most likely is. Filled by [MyAlertViewModel].
 *
 * @property alert The owner's alert, or null while it loads or if it couldn't be loaded.
 * @property sightings Sightings linked to the alert, newest first, with the owner's decision.
 * @property likelyArea Where the pet most likely is; null until a sighting is confirmed.
 * @property isLoading True while the alert is being loaded; the screen starts loading.
 * @property errorMessage Message to show, or null. Set with an [alert] when an action on it failed.
 * @property isClosed True once the owner said the pet is home; the screen then navigates away.
 * @property placeName Where the pet was last seen, e.g. "Ouchy, Lausanne"; null while unknown.
 */
data class MyAlertUiState(
    val alert: Alert? = null,
    val sightings: List<Sighting> = emptyList(),
    val likelyArea: LikelyArea? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    /**
     * Navigation flag set when the alert is closed on load or after [MyAlertViewModel.markFound].
     */
    val isClosed: Boolean = false,
    val placeName: String? = null,
) {
  /** Sightings drawn on the map: dismissed ones are hidden there, but stay greyed in the list. */
  val mapSightings: List<Sighting>
    get() = sightings.filter { it.status != SightingStatus.DISMISSED }
}
