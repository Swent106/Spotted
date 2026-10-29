// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.spotted.data.Pet.PetRepositoryFirestore
import com.android.spotted.data.alert.AlertRepositoryFirestore
import com.android.spotted.data.location.AddressRepositoryGeocoder
import com.android.spotted.data.location.LocationRepositoryProvider
import com.android.spotted.data.photo.PhotoRepositoryFirestore
import com.android.spotted.data.user.UserRepositoryFirestore
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.PetRepository
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertRepository
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.AuthRepositoryProvider
import com.android.spotted.model.auth.User
import com.android.spotted.model.distanceKm
import com.android.spotted.model.formatDistance
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
 * The alerts are searched within the profile's alert radius around the home pin saved in the
 * profile; without a pin, around the place the home area names (found by [addressRepository]), or
 * around [searchCenter] when it can't be found.
 *
 * @property authRepository gives the signed-in user.
 * @property userRepository gives the user's profile.
 * @property petRepository gives the user's pets.
 * @property alertRepository gives the open alerts near a location.
 * @property searchCenter centre of the lost pet search.
 * @property radiusKm radius of the lost pet search when the profile can't be loaded, in km.
 * @property now current time in epoch millis; injected so that tests control it.
 * @property addressRepository finds the position of the home area; null searches [searchCenter].
 * @property photoRepository gives where the profile photo is; null leaves the avatar empty.
 * @property locationRepository gives the device position, for "600 m away"; null measures from the
 *   home.
 */
class HomeViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val petRepository: PetRepository,
    private val alertRepository: AlertRepository,
    private val searchCenter: Location = DEFAULT_SEARCH_CENTER,
    private val radiusKm: Double = DEFAULT_RADIUS_KM,
    private val now: () -> Long = System::currentTimeMillis,
    private val addressRepository: AddressRepository? = null,
    private val photoRepository: PhotoRepository? = null,
    private val locationRepository: LocationRepository? = null,
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
      launch { loadPets(uid) }
      launch { loadOwnLostPets(uid) }
      launch {
        // The lost pets are searched around the user's home, so the profile comes first
        val profile = loadProfile(uid)
        val radius = profile?.alertRadiusKm?.toDouble() ?: radiusKm
        val home = homePositionOf(profile)
        val alerts = loadLostPetsNearby(uid, home ?: searchCenter, radius)
        if (alerts != null) loadDistances(uid, alerts, home)
      }
    }
  }

  /** Loads the header and returns the profile, or null if it can't be loaded. */
  private suspend fun loadProfile(uid: String): UserProfile? {
    // The header can live without the profile, so a failure only leaves it empty
    val profile = userRepository.getUser(uid).getOrNull() ?: return null
    updateFor(uid) {
      it.copy(
          userName = profile.name,
          homeArea = profile.homeArea,
          alertRadiusKm = profile.alertRadiusKm,
          userPhotoUrl = photoRepository?.profilePhotoUrl(uid),
      )
    }
    return profile
  }

  /** The home pin; else the place the home area names; else null. */
  private suspend fun homePositionOf(profile: UserProfile?): Location? {
    profile?.homeLocation?.let {
      return it
    }
    val homeArea = profile?.homeArea
    if (homeArea.isNullOrBlank()) return null
    return addressRepository?.locate(homeArea)?.getOrNull()
  }

  /**
   * Shows how far each of [alerts] is from the device; from [home] when the position can't be read
   * (permission denied, no fix). Without either, the alerts show no distance.
   */
  private suspend fun loadDistances(uid: String, alerts: List<Alert>, home: Location?) {
    val origin = locationRepository?.getCurrentLocation()?.getOrNull() ?: home
    val texts =
        if (origin == null) emptyMap()
        else alerts.associate { it.id to formatDistance(origin.distanceKm(it.lastKnownLocation)) }
    updateFor(uid) { it.copy(distanceTexts = texts) }
  }

  private suspend fun loadPets(uid: String) {
    val result = petRepository.getPetsByOwner(uid)
    updateFor(uid) { it.copy(pets = it.pets.after(result, PETS_LOAD_FAILED)) }
  }


  /** Loads "Lost near you" and returns the alerts, or null if they can't be loaded. */
  private suspend fun loadLostPetsNearby(
      uid: String,
      center: Location,
      radiusKm: Double,
  ): List<Alert>? {
    // The user's own lost pets are not "near you" for them: they are loaded apart, for the map.
    // Sorted here, not in the screen, so the newest alert is featured whatever the repository
    // returns
    val result =
        alertRepository.getOpenAlertsNear(center, radiusKm).map { alerts ->
          alerts
              .filter { it.ownerId != uid }
              .sortedWith(compareByDescending<Alert> { it.lostAtMillis }.thenBy { it.id })
        }
    updateFor(uid) {
      it.copy(
          lostPetsNearby = it.lostPetsNearby.after(result, LOST_PETS_LOAD_FAILED),
          nowMillis = if (result.isSuccess) now() else it.nowMillis,
      )
    }
    return result.getOrNull()
  }

  /** The user's own open alerts, wherever their pet was lost, so the map always shows them. */
  private suspend fun loadOwnLostPets(uid: String) {
    val own = alertRepository.getOpenAlertsByOwner(uid).getOrNull() ?: return
    updateFor(uid) { it.copy(ownLostPets = own) }
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

    /** Geneva city centre: used when the home area can't be found. */
    val DEFAULT_SEARCH_CENTER = Location(latitude = 46.2044, longitude = 6.1432)
    const val DEFAULT_RADIUS_KM = 3.0

    val Factory: ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
        HomeViewModel(
            authRepository = AuthRepositoryProvider.authRepository,
            userRepository = UserRepositoryFirestore(),
            petRepository = PetRepositoryFirestore(),
            alertRepository = AlertRepositoryFirestore(),
            addressRepository = application?.let { AddressRepositoryGeocoder(it) },
            photoRepository = application?.let { PhotoRepositoryFirestore(it) },
            locationRepository = application?.let { LocationRepositoryProvider.create(it) },
        )
      }
    }
  }
}
