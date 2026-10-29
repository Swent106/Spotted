// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.home

import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.user.UserProfile

/**
 * One section of the Home screen ("Your pets", "Lost near you"). Each section loads and fails on
 * its own, so one failing never hides the other.
 *
 * @property items what the section shows.
 * @property isLoading true while the section is being loaded.
 * @property errorMessage message to show when the last load failed, or null.
 */
data class HomeSection<T>(
    val items: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
  /** True when the section loaded successfully and has nothing to show. */
  val isEmpty: Boolean
    get() = items.isEmpty() && !isLoading && errorMessage == null
}

/**
 * UI state of the Home screen.
 *
 * @property userName name of the signed-in user, from their profile.
 * @property homeArea home area typed by the user in their profile ("Plainpalais, Geneva").
 * @property pets pets owned by the signed-in user.
 * @property lostPetsNearby open alerts around the search area, newest first, without the user's own
 *   alerts.
 * @property nowMillis time of the last successful load of [lostPetsNearby], for "lost 2 h ago".
 * @property distanceTexts "600 m away" of each alert of [lostPetsNearby], by alert id: from the
 *   device position, else from the home. An alert without one shows no distance.
 * @property errorMessage screen-wide error (e.g. not signed in), or null.
 */
data class HomeUiState(
    val userName: String = "",
    /** The user's profile photo; the avatar stays empty while there is none. */
    val userPhotoUrl: String? = null,
    val homeArea: String = "",
    /** Radius of the lost pet search, from the profile, in kilometres. */
    val alertRadiusKm: Int = UserProfile.DEFAULT_ALERT_RADIUS_KM,
    val pets: HomeSection<Pet> = HomeSection(),
    val lostPetsNearby: HomeSection<Alert> = HomeSection(),
    /** The user's own open alerts, wherever they are: not in "Lost near you", but on the map. */
    val ownLostPets: List<Alert> = emptyList(),
    val nowMillis: Long = 0L,
    val distanceTexts: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
)
