package com.android.spotted.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.spotted.data.Pet.PetRepositoryFirestore
import com.android.spotted.data.alert.AlertRepositoryFirestore
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.PetRepository
import com.android.spotted.model.alert.AlertRepository
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.AuthRepositoryProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel of the Home screen.
 *
 * Loads the signed-in user's pets and the lost pets (open alerts) around [searchCenter], and
 * exposes them in a single [HomeUiState]. [refresh] reloads both lists.
 *
 * @property authRepository gives the signed-in user.
 * @property petRepository gives the user's pets.
 * @property alertRepository gives the open alerts near a location.
 * @property searchCenter centre of the lost pet search, until the user's home area has coordinates.
 * @property radiusKm radius of the lost pet search, in kilometres.
 * @property now current time in epoch millis; injected so that tests control it.
 */
class HomeViewModel(
    private val authRepository: AuthRepository,
    private val petRepository: PetRepository,
    private val alertRepository: AlertRepository,
    private val searchCenter: Location = DEFAULT_SEARCH_CENTER,
    private val radiusKm: Double = DEFAULT_RADIUS_KM,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

  private val _uiState = MutableStateFlow(HomeUiState())
  val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

  private var loadJob: Job? = null

  init {
    refresh()
  }

  /**
   * Reloads the user's pets and the lost pets nearby. A load that is still running is cancelled, so
   * the state always ends with the latest data. If a call fails, the lists already shown are kept
   * and an error message is set.
   */
  fun refresh() {
    loadJob?.cancel()
    val user = authRepository.currentUser.value
    if (user == null) {
      _uiState.value = HomeUiState(errorMessage = NOT_SIGNED_IN)
      return
    }

    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
    loadJob =
        viewModelScope.launch {
          val pets = petRepository.getPetsByOwner(user.uid)
          val alerts = alertRepository.getOpenAlertsNear(searchCenter, radiusKm)
          _uiState.update { state ->
            state.copy(
                isLoading = false,
                errorMessage = if (pets.isFailure || alerts.isFailure) LOAD_FAILED else null,
                myPets = pets.getOrDefault(state.myPets),
                lostPetsNearby =
                    alerts
                        .map { list -> list.filter { it.ownerId != user.uid } }
                        .getOrDefault(state.lostPetsNearby),
                nowMillis = now(),
            )
          }
        }
  }

  /** Hides the error message. */
  fun clearErrorMsg() {
    _uiState.update { it.copy(errorMessage = null) }
  }

  companion object {
    const val NOT_SIGNED_IN = "You need to be signed in to see your pets."
    const val LOAD_FAILED = "Couldn't load everything. Please try again."

    /** Geneva city centre: used until the home area is saved with coordinates. */
    val DEFAULT_SEARCH_CENTER = Location(latitude = 46.2044, longitude = 6.1432)
    const val DEFAULT_RADIUS_KM = 3.0

    val Factory: ViewModelProvider.Factory = viewModelFactory {
      initializer {
        HomeViewModel(
            authRepository = AuthRepositoryProvider.authRepository,
            petRepository = PetRepositoryFirestore(),
            alertRepository = AlertRepositoryFirestore(),
        )
      }
    }
  }
}
