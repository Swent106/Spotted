// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.spotted.data.Pet.PetRepositoryFirestore
import com.android.spotted.data.alert.AlertRepositoryFirestore
import com.android.spotted.data.user.UserRepositoryFirestore
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.PetRepository
import com.android.spotted.model.alert.AlertRepository
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.AuthRepositoryProvider
import com.android.spotted.model.auth.User
import com.android.spotted.model.user.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel of the Home screen.
 *
 * Follows the signed-in user. For each user it loads, independently:
 * 1. their profile (name and home area), for the header;
 * 2. their pets ("Your pets" section);
 * 3. the open alerts within [radiusKm] of [searchCenter] ("Lost near you" section).
 *
 * When the user changes, the state is reset before anything is loaded and the previous user's loads
 * are cancelled, so the previous user's data is never shown. [refresh] reloads everything and keeps
 * what is already shown until the new data arrives.
 *
 * The home area is still free text, so the alerts are searched around [searchCenter] until the
 * profile stores coordinates.
 *
 * @property authRepository gives the signed-in user.
 * @property userRepository gives the user's profile.
 * @property petRepository gives the user's pets.
 * @property alertRepository gives the open alerts near a location.
 * @property searchCenter centre of the lost pet search.
 * @property radiusKm radius of the lost pet search, in kilometres.
 * @property now current time in epoch millis; injected so that tests control it.
 */
class HomeViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val petRepository: PetRepository,
    private val alertRepository: AlertRepository,
    private val searchCenter: Location = DEFAULT_SEARCH_CENTER,
    private val radiusKm: Double = DEFAULT_RADIUS_KM,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

  private val _uiState = MutableStateFlow(HomeUiState())
  val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

  /** Loads of the user currently shown; cancelled when the user changes or on [refresh]. */
  private var loadJob: Job? = null

  /** Uid of the user whose data is in [uiState], or null. */
  private var shownUid: String? = null

  init {
    viewModelScope.launch { authRepository.currentUser.collect { user -> load(user) } }
  }

  /** Reloads the profile, the pets and the lost pets nearby of the signed-in user. */
  fun refresh() {
    load(authRepository.currentUser.value)
  }

  /**
   * Signs the user out. On success the signed-in user becomes null, so this state is cleared and
   * MainViewModel sends the app back to Sign in. On failure the screen stays as it is, with an
   * error.
   */
  fun signOut() {
    viewModelScope.launch {
      authRepository.signOut().onFailure {
        _uiState.update { state -> state.copy(errorMessage = SIGN_OUT_FAILED) }
      }
    }
  }

  private fun load(user: User?) {
    loadJob?.cancel()
    if (user == null) {
      shownUid = null
      _uiState.value = HomeUiState(errorMessage = NOT_SIGNED_IN)
      return
    }

    val uid = user.uid
    // A different user starts from an empty state: their data must never mix with the previous one
    val start = if (uid == shownUid) _uiState.value else HomeUiState()
    shownUid = uid
    _uiState.value =
        start.copy(
            errorMessage = null,
            pets = start.pets.copy(isLoading = true, errorMessage = null),
            lostPetsNearby = start.lostPetsNearby.copy(isLoading = true, errorMessage = null),
        )

    loadJob = viewModelScope.launch {
      launch { loadProfile(uid) }
      launch { loadPets(uid) }
      launch { loadLostPetsNearby(uid) }
    }
  }

  private suspend fun loadProfile(uid: String) {
    // The header can live without the profile, so a failure only leaves it empty
    val profile = userRepository.getUser(uid).getOrNull() ?: return
    updateFor(uid) { it.copy(userName = profile.name, homeArea = profile.homeArea) }
  }

  private suspend fun loadPets(uid: String) {
    val result = petRepository.getPetsByOwner(uid)
    updateFor(uid) { it.copy(pets = it.pets.after(result, PETS_LOAD_FAILED)) }
  }

  private suspend fun loadLostPetsNearby(uid: String) {
    val result =
        alertRepository.getOpenAlertsNear(searchCenter, radiusKm).map { alerts ->
          alerts.filter { it.ownerId != uid }
        }
    updateFor(uid) {
      it.copy(
          lostPetsNearby = it.lostPetsNearby.after(result, LOST_PETS_LOAD_FAILED),
          nowMillis = if (result.isSuccess) now() else it.nowMillis,
      )
    }
  }

  /** Applies [transform] only if [uid] is still the user shown, so a late result is dropped. */
  private fun updateFor(uid: String, transform: (HomeUiState) -> HomeUiState) {
    if (uid != shownUid) return
    _uiState.update(transform)
  }

  /** New section after a load: the new items on success, the old items and an error on failure. */
  private fun <T> HomeSection<T>.after(result: Result<List<T>>, errorMessage: String) =
      result.fold(
          onSuccess = { HomeSection(items = it) },
          onFailure = { copy(isLoading = false, errorMessage = errorMessage) },
      )

  companion object {
    const val NOT_SIGNED_IN = "You need to be signed in to see your pets."
    const val PETS_LOAD_FAILED = "Couldn't load your pets. Please try again."
    const val LOST_PETS_LOAD_FAILED = "Couldn't load the lost pets near you. Please try again."
    const val SIGN_OUT_FAILED = "Couldn't sign out. Please try again."

    /** Geneva city centre: used until the home area is saved with coordinates. */
    val DEFAULT_SEARCH_CENTER = Location(latitude = 46.2044, longitude = 6.1432)
    const val DEFAULT_RADIUS_KM = 3.0

    val Factory: ViewModelProvider.Factory = viewModelFactory {
      initializer {
        HomeViewModel(
            authRepository = AuthRepositoryProvider.authRepository,
            userRepository = UserRepositoryFirestore(),
            petRepository = PetRepositoryFirestore(),
            alertRepository = AlertRepositoryFirestore(),
        )
      }
    }
  }
}
