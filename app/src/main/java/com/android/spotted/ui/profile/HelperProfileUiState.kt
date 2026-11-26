// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.profile

import com.android.spotted.model.trust.TrustLevel
import com.android.spotted.model.trust.TrustScore
import com.android.spotted.ui.personalinfo.PersonalInfoUiState

/**
 * State of the Helper profile screen (Figma "13 · Helper profile").
 *
 * Sightings are not linked to owners' confirmations yet, so [petsBroughtHome] stays 0 until that
 * data exists; the badges follow from it.
 */
data class HelperProfileUiState(
    val name: String = "",
    /** Shown as "@username" under [name]; empty for a profile saved before it was asked for. */
    val username: String = "",
    val homeArea: String = "",
    /**
     * The user's profile photo; the avatar shows [initial] behind it, and alone when there is none.
     */
    val photoUrl: String? = null,
    val alertRadiusKm: Int = PersonalInfoUiState.DEFAULT_ALERT_RADIUS_KM,
    val petsBroughtHome: Int = 0,
    /** The user's own trust; null while it isn't loaded or if reading it failed. */
    val trust: TrustScore? = null,
    val isLoading: Boolean = false,
    /** True while deleting the account (or signing out when Firebase refuses to delete it). */
    val isLeaving: Boolean = false,
    val errorMessage: String? = null,
    /** True while the "Delete your account?" confirmation is shown. */
    val isDeleteConfirmationVisible: Boolean = false,
) {
  /** The user's own trust level; null while [trust] isn't loaded or if reading it failed. */
  val trustLevel: TrustLevel?
    get() = trust?.level

  /** Letter shown in the avatar while there is no profile photo. */
  val initial: String
    get() = name.trim().firstOrNull()?.uppercase().orEmpty()

  /** "First find" badge: at least one pet brought home. */
  val hasFirstFind: Boolean
    get() = petsBroughtHome >= 1

  /** "5 confirmed" badge: five pets brought home. */
  val hasFiveConfirmed: Boolean
    get() = petsBroughtHome >= FIVE_CONFIRMED

  private companion object {
    const val FIVE_CONFIRMED = 5
  }
}
