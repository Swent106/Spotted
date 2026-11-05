// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.personalinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.android.spotted.data.location.AddressRepositoryGeocoder
import com.android.spotted.data.location.LocationRepositoryProvider
import com.android.spotted.data.photo.PhotoRepositoryFirestore
import com.android.spotted.data.user.UserRepositoryFirestore
import com.android.spotted.model.Location
import com.android.spotted.model.auth.AuthRepositoryProvider
import com.android.spotted.model.auth.User
import com.android.spotted.model.location.AddressRepository
import com.android.spotted.model.location.LocationRepository
import com.android.spotted.model.photo.PhotoRepository
import com.android.spotted.model.user.UserProfile
import com.android.spotted.model.user.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PersonalInfoViewModel(
    private val user: User?,
    private val userRepository: UserRepository,
    private val locationRepository: LocationRepository? = null,
    private val addressRepository: AddressRepository? = null,
    private val photoRepository: PhotoRepository? = null,
) : ViewModel() {

  private val _uiState =
      MutableStateFlow(
          PersonalInfoUiState(email = user?.email.orEmpty(), isSignedOut = user == null)
      )
  val uiState: StateFlow<PersonalInfoUiState> = _uiState.asStateFlow()

  private var profileLoad: Job? = null

  init {
    loadExistingProfile()
  }

  /**
   * Fills the form with the saved profile, if there is one, so editing it doesn't start from empty
   * fields. A new user has no profile yet: the form then stays empty. If the profile can't be read,
   * the form must not look like a new user's, or saving it would overwrite the real profile.
   */
  private fun loadExistingProfile() {
    val signedInUser = user ?: return
    profileLoad = viewModelScope.launch {
      val profile =
          userRepository.getUser(signedInUser.uid).getOrElse { failure ->
            // The repository reports a missing profile as NoSuchElementException
            if (failure !is NoSuchElementException) {
              _uiState.update {
                it.copy(isProfileLoadFailed = true, errorMessage = PROFILE_LOAD_FAILED)
              }
            }
            return@launch
          }
      // The name is saved as one string: the first word is the first name, the rest the last
      // name
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
            // Shows the current photo; it is only uploaded again if the user picks another one
            photoUri = photoRepository?.profilePhotoUrl(signedInUser.uid),
            isEditing = true,
        )
      }
    }
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

  /** Shows the photo the user picked or took ([uri]), or removes it when null. */
  fun onPhotoSelected(uri: String?) {
    _uiState.update { it.copy(photoUri = uri, isSaved = false) }
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

  /**
   * Saves the form as the user's profile. If the saved profile couldn't be read, this reads it
   * again instead, so the form never replaces a profile it didn't load.
   */
  fun saveProfile() {
    if (!_uiState.value.canSave) return
    if (_uiState.value.isProfileLoadFailed) {
      _uiState.update { it.copy(isProfileLoadFailed = false, errorMessage = null) }
      loadExistingProfile()
      return
    }
    val signedInUser = user ?: return

    _uiState.update { it.copy(isSaving = true, errorMessage = null) }
    viewModelScope.launch {
      // Saving before the profile has loaded would overwrite it with what the form held then
      profileLoad?.join()
      val currentState = _uiState.value
      if (currentState.isProfileLoadFailed) {
        _uiState.update { it.copy(isSaving = false) }
        return@launch
      }
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

      // A photo just picked or taken is a local URI: upload it before saving the profile
      val photoUri = currentState.photoUri
      val repository = photoRepository
      if (
          repository != null &&
              photoUri != null &&
              photoUri != repository.profilePhotoUrl(signedInUser.uid)
      ) {
        repository.uploadProfilePhoto(signedInUser.uid, photoUri).onFailure {
          _uiState.update { it.copy(isSaving = false, errorMessage = PHOTO_UPLOAD_FAILED) }
          return@launch
        }
      }
      userRepository
          .saveUser(profile)
          .fold(
              onSuccess = { _uiState.update { it.copy(isSaving = false, isSaved = true) } },
              onFailure = {
                _uiState.update {
                  it.copy(isSaving = false, isSaved = false, errorMessage = SAVE_FAILED)
                }
              },
          )
    }
  }

  /** Called by the View once it has navigated away, so coming back does not navigate again. */
  fun onSavedHandled() {
    _uiState.update { it.copy(isSaved = false) }
  }

  companion object {
    const val PROFILE_LOAD_FAILED =
        "Couldn't load your profile. Check your connection, then tap the button to try again."
    const val SAVE_FAILED = "Couldn't save your profile. Please try again."
    const val PHOTO_UPLOAD_FAILED =
        "Couldn't upload your photo. Try again, or remove it to save without one."
    const val LOCATION_UNAVAILABLE =
        "Couldn't find your position. Tap the map to place your home instead."
    const val LOCATION_PERMISSION_DENIED =
        "Location permission denied. Tap the map to place your home instead."

    val Factory: ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(PersonalInfoViewModel::class.java))
            val application = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]

            return requireNotNull(
                modelClass.cast(
                    PersonalInfoViewModel(
                        user = AuthRepositoryProvider.authRepository.currentUser.value,
                        userRepository = UserRepositoryFirestore(),
                        locationRepository =
                            application?.let { LocationRepositoryProvider.create(it) },
                        addressRepository = application?.let { AddressRepositoryGeocoder(it) },
                        photoRepository = application?.let { PhotoRepositoryFirestore(it) },
                    )
                )
            )
          }
        }
  }
}
