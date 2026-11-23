// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.user

import com.android.spotted.model.Location

/**
 * A user's profile.
 *
 * @property username the name the user chose to be shown as, without the "@"; empty in profiles
 *   saved before it was asked for.
 * @property homeLocation the home pin placed on the map, or null if none was placed; lost pets are
 *   searched around it.
 * @property alertRadiusKm how far from home, in kilometres, lost pets are shown.
 */
data class UserProfile(
    val uid: String,
    val name: String,
    val phone: String = "",
    val email: String,
    val homeArea: String = "",
    val homeLocation: Location? = null,
    val alertRadiusKm: Int = DEFAULT_ALERT_RADIUS_KM,
    val username: String = "",
) {
  companion object {
    const val DEFAULT_ALERT_RADIUS_KM = 3
    /** Same limit as the Firestore rules. */
    const val MAX_USERNAME_LENGTH = 30
  }
}
