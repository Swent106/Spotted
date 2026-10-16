package com.android.spotted.ui.personalinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.android.spotted.data.location.AddressRepositoryGeocoder
import com.android.spotted.data.location.LocationRepositoryFused
import com.android.spotted.data.user.UserRepositoryFirestore
import com.android.spotted.model.Location
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.AuthRepositoryProvider
import com.android.spotted.model.auth.User
import com.android.spotted.model.location.AddressRepository
import com.android.spotted.model.location.LocationRepository
import com.android.spotted.model.user.UserProfile
import com.android.spotted.model.user.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PersonalInfoViewModel(
    private val user: User?,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val locationRepository: LocationRepository? = null,
    private val addressRepository: AddressRepository? = null,
) : ViewModel() {

  private val _uiState =
      MutableStateFlow(
          PersonalInfoUiState(email = user?.email.orEmpty(), isSignedOut = user == null)
      )
  val uiState: StateFlow<PersonalInfoUiState> = _uiState.asStateFlow()

  init {
    loadExistingProfile()
  }

  /**
   * Fills the form with the saved profile, if there is one, so editing it doesn't start from empty
   * fields. A new user has no profile yet: the form then stays empty.
   */
  private fun loadExistingProfile() {
    val signedInUser = user ?: return
    viewModelScope.launch {
      val profile = userRepository.getUser(signedInUser.uid).getOrNull() ?: return@launch
      // The name is saved as one string: the first word is the first name, the rest the last name
      val firstName = profile.name.substringBefore(' ')
      val lastName = profile.name.substringAfter(' ', missingDelimiterValue = "")
      _uiState.update {
        it.copy(
            firstName = firstName,
            lastName = lastName,
            name = profile.name,
            phone = profile.phone,
            homeArea = profile.homeArea,
            homeLocation = profile.homeLocation,
            alertRadiusKm = profile.alertRadiusKm,
            isEditing = true,
        )
      }
    }
  }

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

  /**
   * Places the home pin at [location], as tapped on the map picker. Saved with the profile: lost
   * pets are searched around it.
   */
  fun onHomeLocationChanged(location: Location) {
    _uiState.update { it.copy(homeLocation = location, isSaved = false) }
  }

  /**
   * Places the home pin at the device's current position and writes its neighbourhood and city in
   * the home area field. The View calls it once the location permission is granted; if no position
   * is found, the user can still tap the map. If no place name is found, the pin is still placed
   * and the field is left as it was.
   */
  fun useCurrentLocation() {
    val repository = locationRepository ?: return
    _uiState.update { it.copy(errorMessage = null) }
    viewModelScope.launch {
      val location =
          repository.getCurrentLocation().getOrElse {
            _uiState.update { it.copy(errorMessage = LOCATION_UNAVAILABLE) }
            return@launch
          }
      onHomeLocationChanged(location)
      addressRepository?.areaName(location)?.onSuccess { name -> onHomeAreaChanged(name) }
    }
  }

  /** Called by the View when the user refuses the location permission. */
  fun onLocationPermissionDenied() {
    _uiState.update { it.copy(errorMessage = LOCATION_PERMISSION_DENIED) }
  }

  fun saveProfile() {
    val currentState = _uiState.value
    if (!currentState.canSave) return
    val signedInUser = user ?: return

    val profile =
        UserProfile(
            uid = signedInUser.uid,
            name = currentState.name.trim(),
            phone = currentState.phone,
            email = signedInUser.email,
            homeArea = currentState.homeArea.trim(),
            homeLocation = currentState.homeLocation,
            alertRadiusKm = currentState.alertRadiusKm,
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

  /** Called by the View once it has navigated away, so coming back does not navigate again. */
  fun onSavedHandled() {
    _uiState.update { it.copy(isSaved = false) }
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
    const val LOCATION_UNAVAILABLE =
        "Couldn't find your position. Tap the map to place your home instead."
    const val LOCATION_PERMISSION_DENIED =
        "Location permission denied. Tap the map to place your home instead."

    val Factory: ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(PersonalInfoViewModel::class.java))
            val authRepository = AuthRepositoryProvider.authRepository
            val application = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]

            return requireNotNull(
                modelClass.cast(
                    PersonalInfoViewModel(
                        user = authRepository.currentUser.value,
                        userRepository = UserRepositoryFirestore(),
                        authRepository = authRepository,
                        locationRepository = application?.let { LocationRepositoryFused(it) },
                        addressRepository = application?.let { AddressRepositoryGeocoder(it) },
                    )
                )
            )
          }
        }
  }
}
