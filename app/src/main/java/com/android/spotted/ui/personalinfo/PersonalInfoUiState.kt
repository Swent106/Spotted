package com.android.spotted.ui.personalinfo

import com.android.spotted.model.Location
import com.android.spotted.model.user.UserProfile

data class PersonalInfoUiState(
    val username: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val homeArea: String = "",
    /** Home pin placed on the map picker; lost pets are searched around it. */
    val homeLocation: Location? = null,
    /** Profile photo: the saved one, or one just picked or taken, uploaded when saving. */
    val photoUri: String? = null,
    /** True when the user already has a profile: the form then edits it instead of creating it. */
    val isEditing: Boolean = false,
    val alertRadiusKm: Int = DEFAULT_ALERT_RADIUS_KM,
    val petsBroughtHome: List<String> = emptyList(),
    val achievements: List<String> = emptyList(),
    val helpersThisYear: Int = 0,
    val isSaving: Boolean = false,
    val isLoggingOut: Boolean = false,
    val isSaved: Boolean = false,
    val isSignedOut: Boolean = false,
    val isLoggedOut: Boolean = false,
    val nearbyAlertsRequested: Boolean = false,
    val errorMessage: String? = null,
) {
  val canSave: Boolean
    get() =
        name.isNotBlank() &&
            homeArea.isNotBlank() &&
            !isSaving &&
            !isLoggingOut &&
            !isSignedOut &&
            !isLoggedOut

  companion object {
    val ALERT_RADIUS_OPTIONS_KM = listOf(1, 3, 5, 10)
    const val DEFAULT_ALERT_RADIUS_KM = UserProfile.DEFAULT_ALERT_RADIUS_KM
  }
}
