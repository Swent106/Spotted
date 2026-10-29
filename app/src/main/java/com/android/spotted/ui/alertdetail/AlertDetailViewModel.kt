// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.alertdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.android.spotted.data.alert.AlertRepositoryFirestore
import com.android.spotted.data.location.AddressRepositoryGeocoder
import com.android.spotted.data.location.LocationRepositoryProvider
import com.android.spotted.data.user.UserRepositoryFirestore
import com.android.spotted.model.Location
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertRepository
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.AuthRepositoryProvider
import com.android.spotted.model.distanceKm
import com.android.spotted.model.formatDistance
import com.android.spotted.model.location.AddressRepository
import com.android.spotted.model.location.LocationRepository
import com.android.spotted.model.user.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel of the alert detail screen.
 *
 * Once the alert is loaded, shows how far it is from the device; when the position can't be read
 * (permission denied, no fix), from the signed-in user's home pin, else from the place their home
 * area names. Without any of them, no distance is shown.
 *
 * @property alertRepository gives the alert.
 * @property locationRepository gives the device position; null measures from the home.
 * @property authRepository gives the signed-in user, whose home is the fallback; null: no fallback.
 * @property userRepository gives the signed-in user's profile; null: no fallback.
 * @property addressRepository finds the position of the home area; null uses only the home pin.
 */
class AlertDetailViewModel(
    private val alertRepository: AlertRepository,
    private val locationRepository: LocationRepository? = null,
    private val authRepository: AuthRepository? = null,
    private val userRepository: UserRepository? = null,
    private val addressRepository: AddressRepository? = null,
) : ViewModel() {

  private val _uiState = MutableStateFlow(AlertDetailUiState())
  val uiState: StateFlow<AlertDetailUiState> = _uiState.asStateFlow()

  private var loadJob: Job? = null

  /** Loads the alert identified by [alertId]. */
  fun loadAlert(alertId: String) {
    loadJob?.cancel()
    _uiState.value = AlertDetailUiState(isLoading = true)
    loadJob = viewModelScope.launch {
      alertRepository
          .getAlertById(alertId)
          .fold(
              onSuccess = { alert ->
                _uiState.update {
                  it.copy(alert = alert, isLoading = false, notFound = alert == null)
                }
                if (alert != null) loadDistance(alert)
              },
              onFailure = { failure ->
                _uiState.update {
                  it.copy(
                      isLoading = false,
                      errorMessage = failure.localizedMessage ?: LOAD_FAILED,
                  )
                }
              },
          )
    }
  }

  private suspend fun loadDistance(alert: Alert) {
    val origin = locationRepository?.getCurrentLocation()?.getOrNull() ?: homePosition() ?: return
    _uiState.update {
      it.copy(distanceText = formatDistance(origin.distanceKm(alert.lastKnownLocation)))
    }
  }

  /** The signed-in user's home pin; else the place their home area names; else null. */
  private suspend fun homePosition(): Location? {
    val uid = authRepository?.currentUser?.value?.uid ?: return null
    val profile = userRepository?.getUser(uid)?.getOrNull() ?: return null
    profile.homeLocation?.let {
      return it
    }
    if (profile.homeArea.isBlank()) return null
    return addressRepository?.locate(profile.homeArea)?.getOrNull()
  }

  companion object {
    const val LOAD_FAILED = "Couldn't load the alert. Please try again."

    val Factory: ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          // Without the application, the distance can't use the GPS nor the home area
          override fun <T : ViewModel> create(modelClass: Class<T>): T =
              build(modelClass, null, null)

          override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val application = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
            return build(
                modelClass,
                application?.let { LocationRepositoryProvider.create(it) },
                application?.let { AddressRepositoryGeocoder(it) },
            )
          }

          @Suppress("UNCHECKED_CAST")
          private fun <T : ViewModel> build(
              modelClass: Class<T>,
              locationRepository: LocationRepository?,
              addressRepository: AddressRepository?,
          ): T {
            require(modelClass.isAssignableFrom(AlertDetailViewModel::class.java))
            return AlertDetailViewModel(
                alertRepository = AlertRepositoryFirestore(),
                locationRepository = locationRepository,
                authRepository = AuthRepositoryProvider.authRepository,
                userRepository = UserRepositoryFirestore(),
                addressRepository = addressRepository,
            )
                as T
          }
        }
  }
}
