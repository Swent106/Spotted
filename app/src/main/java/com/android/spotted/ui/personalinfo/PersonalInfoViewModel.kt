package com.android.spotted.ui.personalinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.spotted.data.user.UserRepositoryFirestore
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.AuthRepositoryProvider
import com.android.spotted.model.auth.User
import com.android.spotted.model.user.UserProfile
import com.android.spotted.model.user.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PersonalInfoViewModel(
    private val user: User,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(PersonalInfoUiState(email = user.email))
  val uiState: StateFlow<PersonalInfoUiState> = _uiState.asStateFlow()

  fun onNameChanged(name: String) {
    _uiState.update { it.copy(name = name, isSaved = false, errorMessage = null) }
  }

  fun onUsernameChanged(username: String) {
    _uiState.update { it.copy(username = username, isSaved = false, errorMessage = null) }
  }

  /** First and last name are edited separately but saved together as [PersonalInfoUiState.name]. */
  fun onFirstNameChanged(firstName: String) {
    _uiState.update {
      it.copy(
          firstName = firstName,
          name = fullName(firstName, it.lastName),
          isSaved = false,
          errorMessage = null,
      )
    }
  }

  fun onLastNameChanged(lastName: String) {
    _uiState.update {
      it.copy(
          lastName = lastName,
          name = fullName(it.firstName, lastName),
          isSaved = false,
          errorMessage = null,
      )
    }
  }

  fun onAlertRadiusChanged(radiusKm: Int) {
    if (radiusKm !in PersonalInfoUiState.ALERT_RADIUS_OPTIONS_KM) return
    _uiState.update { it.copy(alertRadiusKm = radiusKm, isSaved = false) }
  }

  private fun fullName(firstName: String, lastName: String): String =
      listOf(firstName.trim(), lastName.trim()).filter { it.isNotEmpty() }.joinToString(" ")

  fun onPhoneChanged(phone: String) {
    _uiState.update { it.copy(phone = phone, isSaved = false, errorMessage = null) }
  }

  fun onHomeAreaChanged(homeArea: String) {
    _uiState.update { it.copy(homeArea = homeArea, isSaved = false, errorMessage = null) }
  }

  fun saveProfile() {
    val currentState = _uiState.value
    if (!currentState.canSave) return

    val profile =
        UserProfile(
            uid = user.uid,
            name = currentState.name.trim(),
            phone = currentState.phone,
            email = user.email,
            homeArea = currentState.homeArea.trim(),
        )

    _uiState.update { it.copy(isSaving = true, errorMessage = null) }
    viewModelScope.launch {
      userRepository
          .saveUser(profile)
          .fold(
              onSuccess = { _uiState.update { it.copy(isSaving = false, isSaved = true) } },
              onFailure = { failure ->
                _uiState.update {
                  it.copy(
                      isSaving = false,
                      isSaved = false,
                      errorMessage = failure.localizedMessage ?: "Unable to save profile",
                  )
                }
              },
          )
    }
  }

  fun logout() {
    if (_uiState.value.isLoggingOut || _uiState.value.isLoggedOut) return

    _uiState.update { it.copy(isLoggingOut = true, errorMessage = null) }
    viewModelScope.launch {
      authRepository
          .signOut()
          .fold(
              onSuccess = { _uiState.update { it.copy(isLoggingOut = false, isLoggedOut = true) } },
              onFailure = { failure ->
                _uiState.update {
                  it.copy(
                      isLoggingOut = false,
                      isLoggedOut = false,
                      errorMessage = failure.localizedMessage ?: "Unable to log out",
                  )
                }
              },
          )
    }
  }

  fun requestNearbyAlerts() {
    _uiState.update { it.copy(nearbyAlertsRequested = true) }
  }

  fun clearNearbyAlertsRequest() {
    _uiState.update { it.copy(nearbyAlertsRequested = false) }
  }

  companion object {
    val Factory: ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PersonalInfoViewModel::class.java))
            val authRepository = AuthRepositoryProvider.authRepository
            val user =
                authRepository.currentUser.value
                    ?: error("A signed-in user is required for Personal Info")

            return PersonalInfoViewModel(
                user = user,
                userRepository = UserRepositoryFirestore(),
                authRepository = authRepository,
            )
                as T
          }
        }
  }
}
