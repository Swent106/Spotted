package com.android.spotted.ui.home

import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.alert.Alert

/**
 * UI state of the Home screen.
 *
 * @property isLoading true while the pets and the lost pets are being loaded.
 * @property errorMessage message to show when loading failed, or null.
 * @property myPets pets owned by the signed-in user.
 * @property lostPetsNearby open alerts around the search area, newest first, without the user's
 *   own alerts.
 * @property nowMillis time of the last successful load, used to show "lost 2 h ago".
 */
data class HomeUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val myPets: List<Pet> = emptyList(),
    val lostPetsNearby: List<Alert> = emptyList(),
    val nowMillis: Long = 0L,
) {
  /** True when the user has no pet and nothing is loading or failing. */
  val hasNoPets: Boolean
    get() = myPets.isEmpty() && !isLoading && errorMessage == null

  /** True when there is no lost pet nearby and nothing is loading or failing. */
  val hasNoLostPetsNearby: Boolean
    get() = lostPetsNearby.isEmpty() && !isLoading && errorMessage == null
}
