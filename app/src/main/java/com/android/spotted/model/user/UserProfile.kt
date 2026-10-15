package com.android.spotted.model.user

import com.android.spotted.model.Location

/**
 * A user's profile.
 *
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
) {
  companion object {
    const val DEFAULT_ALERT_RADIUS_KM = 3
  }
}
