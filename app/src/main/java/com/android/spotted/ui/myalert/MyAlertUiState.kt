// Portions of this code were generated with the help of Copilot.
// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.myalert

import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertStatus
import com.android.spotted.model.sighting.LikelyArea
import com.android.spotted.model.sighting.Sighting
import com.android.spotted.model.sighting.SightingStatus
import com.android.spotted.model.trust.TrustLevel

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
 *
 * "Mark as found" goes through four states: idle ([canMarkFound]), closing ([isClosing]), closed
 * ([isClosed], the hook the thanks and found form flows start from) and failure ([errorMessage]).
 */
data class MyAlertUiState(
    val alert: Alert? = null,
    val sightings: List<Sighting> = emptyList(),
    /** Available reporter trust levels, keyed by reporter ID; absent when the read fails. */
    val reporterTrustLevels: Map<String, TrustLevel> = emptyMap(),
    val likelyArea: LikelyArea? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    /**
     * Navigation flag set when the alert is closed on load or after [MyAlertViewModel.markFound].
     */
    val isClosed: Boolean = false,
    val placeName: String? = null,
    /** True while [MyAlertViewModel.markFound] is closing the alert. */
    val isClosing: Boolean = false,
) {
  /**
   * The owner can say the pet is home: the alert is open and not being closed. Only the owner gets
   * an [alert] (anyone else gets an error instead), so this is never true for someone else.
   */
  val canMarkFound: Boolean
    get() = alert?.status == AlertStatus.OPEN && !isClosed && !isClosing

  /**
   * Sightings drawn on the map: dismissed ones and those voted false are hidden there, but stay in
   * the list (greyed, or labelled "Hidden").
   */
  val mapSightings: List<Sighting>
    get() = sightings.filter { it.status !in HIDDEN_FROM_MAP }

  private companion object {
    val HIDDEN_FROM_MAP = setOf(SightingStatus.DISMISSED, SightingStatus.VOTED_FALSE)
  }
}
