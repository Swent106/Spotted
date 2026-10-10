package com.android.spotted.ui.personalinfo

data class PersonalInfoUiState(
    val username: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val homeArea: String = "",
    val alertRadiusKm: Int = DEFAULT_ALERT_RADIUS_KM,
    val petsBroughtHome: List<String> = emptyList(),
    val achievements: List<String> = emptyList(),
    val helpersThisYear: Int = 0,
    val isSaving: Boolean = false,
    val isLoggingOut: Boolean = false,
    val isSaved: Boolean = false,
    val isLoggedOut: Boolean = false,
    val nearbyAlertsRequested: Boolean = false,
    val errorMessage: String? = null,
) {
  val canSave: Boolean
    get() = name.isNotBlank() && homeArea.isNotBlank() && !isSaving

  companion object {
    val ALERT_RADIUS_OPTIONS_KM = listOf(1, 3, 5, 10)
    const val DEFAULT_ALERT_RADIUS_KM = 3
  }
}
