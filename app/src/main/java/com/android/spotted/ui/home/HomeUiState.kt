// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.home

import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.Species
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
 * A sighting made offline and kept on the phone until it can be sent ("Waiting to send" sheet).
 *
 * @property id id of the sighting.
 * @property petName name of the pet of the sighting's alert, or null without an alert (or when the
 *   alert can't be loaded): the row then shows [species].
 * @property species species of the animal seen.
 * @property photoUri local URI of its photo, if it has one.
 * @property savedAtMillis when the animal was seen, i.e. when the sighting was saved.
 * @property place where it was seen: the area name, or the coordinates when it can't be found.
 */
data class WaitingSighting(
    val id: String,
    val petName: String?,
    val species: Species,
    val photoUri: String?,
    val savedAtMillis: Long,
    val place: String,
)

/**
 * UI state of the Home screen.
 *
 * @property userName name of the signed-in user, from their profile.
 * @property homeArea home area typed by the user in their profile ("Plainpalais, Geneva").
 * @property pets pets owned by the signed-in user.
 * @property lostPetsNearby open alerts around the search area, newest first, without the user's own
 *   alerts.
 * @property carouselAlerts nearby open alerts for the carousel, nearest first, without the user's
 *   own alerts or alerts from blocked users.
 * @property nowMillis time of the last successful load of [lostPetsNearby], for "lost 2 h ago".
 * @property distanceTexts "600 m away" of each alert of [lostPetsNearby], by alert id: from the
 *   device position, else from the home. An alert without one shows no distance.
 * @property errorMessage screen-wide error (e.g. not signed in), or null.
 * @property waitingSightings the signed-in user's sightings waiting to be sent, oldest first.
 * @property isWaitingSheetOpen true while the "Waiting to send" sheet is shown.
 * @property waitingDeleteId the waiting sighting whose deletion is being confirmed, or null.
 * @property sentSighting a waiting sighting that was just sent, for the "was sent" snackbar.
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
    val carouselAlerts: HomeSection<Alert> = HomeSection(),
    /** The user's own open alerts, wherever they are: not in "Lost near you", but on the map. */
    val ownLostPets: List<Alert> = emptyList(),
    val nowMillis: Long = 0L,
    val distanceTexts: Map<String, String> = emptyMap(),
    val isOffline: Boolean = false,
    val errorMessage: String? = null,
    val waitingSightings: List<WaitingSighting> = emptyList(),
    val isWaitingSheetOpen: Boolean = false,
    val waitingDeleteId: String? = null,
    val sentSighting: WaitingSighting? = null,
    /** New sightings waiting for the user's review on their alerts: the badge on the bell. */
    val pendingSightings: Int = 0,
) {
  /** The open alert of each missing pet, by pet id: those are missing, the others are safe. */
  val alertByPetId: Map<String, Alert>
    get() = ownLostPets.associateBy { it.petId }

  /**
   * Open alerts whose pet is not in [pets] (its profile failed to load or is gone): Home still
   * shows them, so the owner can always reach their alert.
   */
  val lostPetsNotListed: List<Alert>
    get() {
      val listed = pets.items.mapTo(mutableSetOf()) { it.id }
      return ownLostPets.filterNot { it.petId in listed }
    }
}
