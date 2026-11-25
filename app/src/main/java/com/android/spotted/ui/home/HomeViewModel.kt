// Portions of this code were generated with the help of Claude (Anthropic).
// Portions of this code were generated with the help of Copilot.
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
import com.android.spotted.data.moderation.BlockRepositoryFirestore
import com.android.spotted.data.notification.NotificationRepositoryDefault
import com.android.spotted.data.photo.PhotoRepositoryFirestore
import com.android.spotted.data.sighting.PendingSightingRepositoryProvider
import com.android.spotted.data.sighting.SightingRepositoryFirestore
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
import com.android.spotted.model.moderation.BlockRepository
import com.android.spotted.model.notification.NotificationRepository
import com.android.spotted.model.photo.PhotoRepository
import com.android.spotted.model.sighting.PendingSighting
import com.android.spotted.model.sighting.PendingSightingRepository
import com.android.spotted.model.user.UserProfile
import com.android.spotted.model.user.UserRepository
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
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
 * @property pendingSightingRepository the sightings made offline and waiting to be sent; null shows
 *   none.
 * @property notificationRepository counts the new sightings for the bell's badge; null shows none.
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
    private val pendingSightingRepository: PendingSightingRepository? = null,
    private val notificationRepository: NotificationRepository? = null,
    private val blockRepository: BlockRepository? = null,
) : ViewModel() {

  private val _uiState = MutableStateFlow(HomeUiState())
  val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

  /** Loads of the user currently shown; cancelled when the user changes or on [refresh]. */
  private var loadJob: Job? = null

  /** Uid of the user whose data is in [uiState], or null. */
  private var shownUid: String? = null

  /** Every queued sighting, of any user, with who reported it, as last listed. */
  private var queued: List<Pair<String, WaitingSighting>> = emptyList()

  /** Waiting sightings the user deleted: when they leave the queue, they were not sent. */
  private val deletingIds = mutableSetOf<String>()

  /**
   * Rows whose pet name and place were found, so they are not looked up again. Keyed by the whole
   * queued sighting, so a sighting replaced under the same id is looked up again.
   */
  private val rowCache = mutableMapOf<PendingSighting, WaitingSighting>()

  init {
    viewModelScope.launch { authRepository.currentUser.collect { user -> load(user) } }
    pendingSightingRepository?.let { repository ->
      viewModelScope.launch {
        repository.observeQueue().collectLatest { queue -> queue.onSuccess { reloadWaiting(it) } }
      }
    }
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
        withWaiting(
            start.copy(
                errorMessage = null,
                pets = start.pets.copy(isLoading = true, errorMessage = null),
                lostPetsNearby = start.lostPetsNearby.copy(isLoading = true, errorMessage = null),
                carouselAlerts = start.carouselAlerts.copy(isLoading = true, errorMessage = null),
            )
        )

    loadJob = viewModelScope.launch {
      launch { loadPets(uid) }
      launch { loadOwnLostPets(uid) }
      launch { observePendingSightings(uid) }
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
          sortNearbyAlerts(alerts, uid)
        }
    val cachedAlerts =
        if (result.isFailure) {
          alertRepository
              .getCachedOpenAlertsNear(
                  center,
                  radiusKm,
                  limit = AlertRepository.MY_ALERTS_LIMIT,
              )
              .getOrNull()
              ?.let { sortNearbyAlerts(it, uid).take(OFFLINE_ALERTS_LIMIT) }
        } else {
          null
        }
    val usingCache = !cachedAlerts.isNullOrEmpty()
    val displayedResult = if (usingCache) Result.success(cachedAlerts.orEmpty()) else result
    val carouselResult =
        if (displayedResult.isFailure) displayedResult
        else {
          val blockedResult = blockRepository?.observeBlocked(uid)?.first()
          if (blockedResult?.isFailure == true) {
            Result.failure(blockedResult.exceptionOrNull()!!)
          } else {
            val blocked = blockedResult?.getOrNull().orEmpty()
            Result.success(
                displayedResult
                    .getOrThrow()
                    .filterNot { it.ownerId in blocked }
                    .sortedBy { center.distanceKm(it.lastKnownLocation) }
                    .take(CAROUSEL_ALERTS_LIMIT)
            )
          }
        }
    updateFor(uid) {
      it.copy(
          lostPetsNearby = it.lostPetsNearby.after(displayedResult, LOST_PETS_LOAD_FAILED),
          carouselAlerts = it.carouselAlerts.after(carouselResult, LOST_PETS_LOAD_FAILED),
          nowMillis = if (displayedResult.isSuccess) now() else it.nowMillis,
          isOffline = usingCache,
      )
    }
    return displayedResult.getOrNull()
  }

  private fun sortNearbyAlerts(alerts: List<Alert>, uid: String): List<Alert> =
      alerts
          .filter { it.ownerId != uid }
          .sortedWith(compareByDescending<Alert> { it.lostAtMillis }.thenBy { it.id })

  /** The user's own open alerts, wherever their pet was lost, so the map always shows them. */
  private suspend fun loadOwnLostPets(uid: String) {
    val own = alertRepository.getOpenAlertsByOwner(uid).getOrNull() ?: return
    updateFor(uid) { it.copy(ownLostPets = own) }
  }

  /** Opens the "Waiting to send" sheet, if a sighting is waiting. */
  fun openWaitingSheet() {
    _uiState.update { it.copy(isWaitingSheetOpen = it.waitingSightings.isNotEmpty()) }
  }

  fun closeWaitingSheet() {
    _uiState.update { it.copy(isWaitingSheetOpen = false, waitingDeleteId = null) }
  }

  /** Asks to confirm the deletion of the waiting sighting [sightingId]. */
  fun askToDeleteWaiting(sightingId: String) {
    _uiState.update { state ->
      if (state.waitingSightings.none { it.id == sightingId }) state
      else state.copy(waitingDeleteId = sightingId)
    }
  }

  fun cancelDeleteWaiting() {
    _uiState.update { it.copy(waitingDeleteId = null) }
  }

  /**
   * Deletes the waiting sighting being confirmed, with its photo, so it is not sent later. A send
   * already under way is not stopped. Its row goes away when the queue says it is gone.
   */
  fun confirmDeleteWaiting() {
    val repository = pendingSightingRepository ?: return
    val sightingId = _uiState.value.waitingDeleteId ?: return
    deletingIds += sightingId
    _uiState.update { it.copy(waitingDeleteId = null) }
    viewModelScope.launch {
      repository.delete(sightingId).onFailure {
        deletingIds -= sightingId
        _uiState.update { it.copy(errorMessage = WAITING_DELETE_FAILED) }
      }
    }
  }

  /** The "was sent" snackbar was shown. */
  fun sentSightingShown() {
    _uiState.update { it.copy(sentSighting = null) }
  }

  /**
   * Lists the queue again. A sighting that left it without being deleted by the user was sent: the
   * last one of the user shown is told with [HomeUiState.sentSighting].
   */
  private suspend fun reloadWaiting(pending: List<PendingSighting>) {
    val rows = pending.map { it.sighting.reporterId to waitingRowOf(it) }
    val ids = rows.mapTo(mutableSetOf()) { it.second.id }
    val sent = queued.lastOrNull { (reporterId, row) ->
      reporterId == shownUid && row.id !in ids && row.id !in deletingIds
    }
    deletingIds.retainAll(ids)
    rowCache.keys.retainAll(pending.toSet())
    queued = rows
    _uiState.update { state ->
      withWaiting(state).copy(sentSighting = sent?.second ?: state.sentSighting)
    }
  }

  /** [state] with the waiting sightings of the user shown; the sheet closes once none is left. */
  private fun withWaiting(state: HomeUiState): HomeUiState {
    val mine = queued.filter { it.first == shownUid }.map { it.second }
    return state.copy(
        waitingSightings = mine,
        isWaitingSheetOpen = state.isWaitingSheetOpen && mine.isNotEmpty(),
        waitingDeleteId = state.waitingDeleteId?.takeIf { id -> mine.any { it.id == id } },
    )
  }

  /**
   * The row of [pending]: its alert's pet name and its area name, when they can be found (offline,
   * they often can't: then the species and the coordinates).
   */
  private suspend fun waitingRowOf(pending: PendingSighting): WaitingSighting {
    val sighting = pending.sighting
    rowCache[pending]?.let {
      return it
    }
    val petName = sighting.alertId?.let { alertRepository.getAlertById(it).getOrNull()?.petName }
    val area = addressRepository?.areaName(sighting.location)?.getOrNull()
    val row =
        WaitingSighting(
            id = sighting.id,
            petName = petName,
            species = sighting.species,
            photoUri = pending.photoUri,
            savedAtMillis = sighting.seenAtMillis,
            place =
                area
                    ?: "%.4f, %.4f"
                        .format(Locale.US, sighting.location.latitude, sighting.location.longitude),
        )
    val complete = area != null && (sighting.alertId == null || petName != null)
    if (complete) rowCache[pending] = row
    return row
  }

  /** Applies [transform] only if [uid] is still the user shown, so a late result is dropped. */
  /** Keeps the bell's badge up to date; a failure keeps the last count. */
  private suspend fun observePendingSightings(uid: String) {
    val repository = notificationRepository ?: return
    repository.observePendingSightings(uid).collect { result ->
      result.onSuccess { pending -> updateFor(uid) { it.copy(pendingSightings = pending.size) } }
    }
  }

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
    const val WAITING_DELETE_FAILED = "Couldn't delete this sighting. Please try again."
    private const val OFFLINE_ALERTS_LIMIT = 5
    private const val CAROUSEL_ALERTS_LIMIT = 10

    /** Geneva city centre: used when the home area can't be found. */
    val DEFAULT_SEARCH_CENTER = Location(latitude = 46.2044, longitude = 6.1432)
    const val DEFAULT_RADIUS_KM = 3.0

    val Factory: ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val application =
            requireNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
        val photoRepository = PhotoRepositoryFirestore(application)
        val alertRepository = AlertRepositoryFirestore()
        HomeViewModel(
            authRepository = AuthRepositoryProvider.authRepository,
            userRepository = UserRepositoryFirestore(),
            petRepository = PetRepositoryFirestore(photoRepository = photoRepository),
            alertRepository = alertRepository,
            addressRepository = AddressRepositoryGeocoder(application),
            photoRepository = photoRepository,
            locationRepository = LocationRepositoryProvider.create(application),
            pendingSightingRepository = PendingSightingRepositoryProvider.get(application),
            notificationRepository =
                NotificationRepositoryDefault(alertRepository, SightingRepositoryFirestore()),
            blockRepository = BlockRepositoryFirestore(),
        )
      }
    }
  }
}
