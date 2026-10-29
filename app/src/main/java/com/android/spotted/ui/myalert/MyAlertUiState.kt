// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.myalert

import com.android.spotted.model.alert.Alert
import com.android.spotted.model.sighting.LikelyArea
import com.android.spotted.model.sighting.Sighting
import com.android.spotted.model.sighting.SightingStatus

/**
 * State of the owner's "My alert" screen: one of their alerts, the sightings reported on it, and
 * where the pet most likely is. This is the contract `MyAlertViewModel` (#179) fills.
 *
 * @property alert The owner's alert, or null while it loads or if it couldn't be loaded.
 * @property sightings Sightings linked to the alert, newest first, with the owner's decision.
 * @property likelyArea Where the pet most likely is; null until a sighting is confirmed.
 * @property isLoading True while the alert is being loaded.
 * @property errorMessage Message to show, or null.
 * @property isClosed True once the owner said the pet is home; the screen then navigates away.
 */
data class MyAlertUiState(
    val alert: Alert? = null,
    val sightings: List<Sighting> = emptyList(),
    val likelyArea: LikelyArea? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isClosed: Boolean = false,
) {
  /** Sightings drawn on the map: dismissed ones are hidden there, but stay greyed in the list. */
  val mapSightings: List<Sighting>
    get() = sightings.filter { it.status != SightingStatus.DISMISSED }
}
