package com.android.spotted.ui.personalinfo

data class PersonalInfoUiState(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val homeArea: String = "",
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
}
