// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.personalinfo

import com.android.spotted.model.Location
import com.android.spotted.model.user.UserProfile

data class PersonalInfoUiState(
    val username: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val name: String = "",
    val email: String = "",
    /** Not edited on this screen, but kept so that saving doesn't erase the stored phone. */
    val phone: String = "",
    val homeArea: String = "",
    /** Home pin placed on the map picker; lost pets are searched around it. */
    val homeLocation: Location? = null,
    /** Profile photo: the saved one, or one just picked or taken, uploaded when saving. */
    val photoUri: String? = null,
    /** True when the user already has a profile: the form then edits it instead of creating it. */
    val isEditing: Boolean = false,
    val alertRadiusKm: Int = DEFAULT_ALERT_RADIUS_KM,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val isSignedOut: Boolean = false,
    val errorMessage: String? = null,
) {
  val canSave: Boolean
    get() = name.isNotBlank() && homeArea.isNotBlank() && !isSaving && !isSignedOut

  companion object {
    val ALERT_RADIUS_OPTIONS_KM = listOf(1, 3, 5, 10)
    const val DEFAULT_ALERT_RADIUS_KM = UserProfile.DEFAULT_ALERT_RADIUS_KM
  }
}
