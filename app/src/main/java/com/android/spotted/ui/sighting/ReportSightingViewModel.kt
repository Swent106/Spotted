// Portions of this code were generated with the help of Copilot.
// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.sighting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.android.spotted.data.alert.AlertRepositoryFirestore
import com.android.spotted.data.location.LocationRepositoryProvider
import com.android.spotted.data.network.NetworkMonitorAndroid
import com.android.spotted.data.photo.AnimalDetectorMlKit
import com.android.spotted.data.photo.PhotoRepositoryFirestore
import com.android.spotted.data.sighting.SightingRepositoryFirestore
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertRepository
import com.android.spotted.model.alert.AlertStatus
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.AuthRepositoryProvider
import com.android.spotted.model.distanceKm
import com.android.spotted.model.location.LocationRepository
import com.android.spotted.model.network.NetworkMonitor
import com.android.spotted.model.photo.AnimalDetector
import com.android.spotted.model.photo.PhotoRepository
import com.android.spotted.model.sighting.AnimalActivity
import com.android.spotted.model.sighting.AnimalCondition
import com.android.spotted.model.sighting.AnimalReaction
import com.android.spotted.model.sighting.CollarStatus
import com.android.spotted.model.sighting.PendingSightingRepository
import com.android.spotted.model.sighting.PlaceType
import com.android.spotted.model.sighting.PlausibilityIssue
import com.android.spotted.model.sighting.Sighting
import com.android.spotted.model.sighting.SightingRepository
import com.android.spotted.model.sighting.checkPlausibility
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReportSightingViewModel(
    private val sightingRepository: SightingRepository,
    private val photoRepository: PhotoRepository,
    private val locationRepository: LocationRepository? = null,
    private val authRepository: AuthRepository = AuthRepositoryProvider.authRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val alertRepository: AlertRepository? = null,
    private val animalDetector: AnimalDetector? = null,
    /** Where a sighting sent offline is queued; without one, an offline send shows an error. */
    private val pendingSightingRepository: PendingSightingRepository? = null,
    /** Tells whether Send can reach the server; without one, the phone is taken to be online. */
    private val networkMonitor: NetworkMonitor? = null,
) : ViewModel() {

  /** Opens offline when [networkMonitor] says so, so the screen warns before Send. */
  private val _uiState =
      MutableStateFlow(ReportSightingUiState(isOffline = networkMonitor?.isOnline() == false))
  val uiState: StateFlow<ReportSightingUiState> = _uiState.asStateFlow()

  /** Detection of the current photo, cancelled when another photo is chosen. */
  private var photoCheckJob: Job? = null

  private var nearbyAlertsJob: Job? = null
  private var nearbyAlertsRequestId = 0L
  private var nearbyAlertCandidates: List<Alert> = emptyList()
  private var preselectedAlert: Alert? = null
  private var speciesChosenByUser: Species? = null

  fun loadCurrentLocation() {
    val repo = locationRepository ?: return
    viewModelScope.launch {
      val result = repo.getCurrentLocation()
      result.fold(
          onSuccess = { loc -> setLocation(loc, "Taken from your GPS") },
          onFailure = { error ->
            _uiState.update {
              it.copy(
                  errorMessage =
                      error.localizedMessage ?: "Location unavailable. Tap to set location on map.",
              )
            }
          },
      )
    }
  }

  fun setSpecies(species: Species) {
    speciesChosenByUser = species
    _uiState.update { it.copy(selectedSpecies = species) }
    refreshNearbyAlertOptions()
  }

  fun setLocation(location: Location, address: String = "Location on map") {
    _uiState.update { it.copy(location = location, locationAddress = address) }
    loadNearbyAlerts(location)
  }

  fun setDirection(degrees: Int?) {
    val normalized = degrees?.let { ((it % 360) + 360) % 360 }
    _uiState.update { it.copy(direction = normalized) }
  }

  /**
   * Opens the form for the alert [alertId] (from "I have seen this pet"): loads it so it is listed
   * with its pet's species, and selects it.
   */
  fun preselectAlert(alertId: String) {
    val repository = alertRepository
    if (repository == null) {
      selectAlert(alertId)
      return
    }
    viewModelScope.launch {
      repository
          .getAlertById(alertId)
          .getOrNull()
          ?.takeIf { it.status == AlertStatus.OPEN }
          ?.let { alert ->
            preselectedAlert = alert
            refreshNearbyAlertOptions()
            selectAlert(alertId)
          }
    }
  }

  /** Lists the open alerts around [location], so the user can say which pet they saw. */
  private fun loadNearbyAlerts(location: Location) {
    nearbyAlertsJob?.cancel()
    val requestId = ++nearbyAlertsRequestId
    nearbyAlertCandidates = emptyList()
    _uiState.update { it.copy(isNearbyAlertsLoaded = false) }
    refreshNearbyAlertOptions()

    val repository = alertRepository
    if (repository == null) {
      _uiState.update { it.copy(isNearbyAlertsLoaded = true) }
      refreshNearbyAlertOptions()
      return
    }

    nearbyAlertsJob = viewModelScope.launch {
      repository.getOpenAlertsNear(location, NEARBY_RADIUS_KM).onSuccess { alerts ->
        if (requestId != nearbyAlertsRequestId) return@onSuccess
        nearbyAlertCandidates = alerts
        _uiState.update { it.copy(isNearbyAlertsLoaded = true) }
        refreshNearbyAlertOptions()
      }
    }
  }

  /** Refilters the last nearby-alert result whenever position or species changes. */
  private fun refreshNearbyAlertOptions() {
    _uiState.update { state ->
      val species = speciesChosenByUser ?: state.detectedSpecies
      val ownerId = authRepository.currentUser.value?.uid
      val nearby =
          state.location
              ?.let { location ->
                nearbyAlertCandidates
                    .asSequence()
                    .filter { it.status == AlertStatus.OPEN }
                    .filter { it.ownerId != ownerId }
                    .filter { species == null || it.petSpecies == species }
                    .map { alert -> alert to location.distanceKm(alert.lastKnownLocation) }
                    .filter { (_, distance) -> distance <= NEARBY_RADIUS_KM }
                    .sortedWith(compareBy<Pair<Alert, Double>> { it.second }.thenBy { it.first.id })
                    .map { it.first }
                    .toList()
              }
              .orEmpty()
      val pinned = preselectedAlert
      val ordinary =
          nearby
              .filter { it.id != pinned?.id }
              .take(MAX_NEARBY_ALERTS - if (pinned == null) 0 else 1)
      val options = (listOfNotNull(pinned) + ordinary).map(::toNearbyAlertOption)
      val selectedAlertId = state.selectedAlertId
      val selectionWasRemoved =
          selectedAlertId != null && options.none { it.alertId == selectedAlertId }
      val shouldSelectNoMatch =
          selectionWasRemoved || (state.isNearbyAlertsLoaded && options.isEmpty())

      state.copy(
          nearbyAlerts = options,
          selectedAlertId = if (selectionWasRemoved) null else selectedAlertId,
          isNoMatchingAlertSelected =
              when {
                shouldSelectNoMatch -> true
                selectedAlertId != null -> false
                else -> state.isNoMatchingAlertSelected
              },
      )
    }
  }

  private fun toNearbyAlertOption(alert: Alert) =
      NearbyAlertOption(
          alertId = alert.id,
          petName = alert.petName,
          description = alert.petSpecies.name.lowercase().replaceFirstChar(Char::titlecase),
          photoUrl = alert.petPhotoUrl,
          species = alert.petSpecies,
      )

  /**
   * Shows [uri] as the sighting photo and checks that it shows an animal. Removing the photo (null)
   * resets the check; if the detector fails, the photo is accepted so the user is never blocked.
   */
  fun setPhotoUri(uri: String?) {
    photoCheckJob?.cancel()
    val detector = animalDetector
    if (uri == null || detector == null) {
      val check = if (uri == null) PhotoCheck.NONE else PhotoCheck.ANIMAL
      _uiState.update { it.copy(photoUri = uri, photoCheck = check, detectedSpecies = null) }
      refreshNearbyAlertOptions()
      return
    }
    _uiState.update {
      it.copy(photoUri = uri, photoCheck = PhotoCheck.CHECKING, detectedSpecies = null)
    }
    refreshNearbyAlertOptions()
    photoCheckJob = viewModelScope.launch {
      detector
          .detect(uri)
          .fold(
              onSuccess = { detection ->
                _uiState.update {
                  if (detection.isAnimal)
                      it.copy(photoCheck = PhotoCheck.ANIMAL, detectedSpecies = detection.species)
                  else it.copy(photoCheck = PhotoCheck.NO_ANIMAL)
                }
                refreshNearbyAlertOptions()
              },
              // Offline or failed: accept the photo, without a species
              onFailure = { _uiState.update { it.copy(photoCheck = PhotoCheck.ANIMAL) } },
          )
    }
  }

  fun selectAlert(alertId: String) {
    _uiState.update { currentState ->
      val alert = currentState.nearbyAlerts.firstOrNull { it.alertId == alertId }
      val autoSpecies =
          alert?.species ?: currentState.selectedSpecies ?: currentState.detectedSpecies
      currentState.copy(
          selectedAlertId = alertId,
          isNoMatchingAlertSelected = false,
          selectedSpecies = autoSpecies,
      )
    }
    if (uiState.value.nearbyAlerts.any { it.alertId == alertId }) {
      refreshNearbyAlertOptions()
    }
  }

  fun selectNoMatchingAlert() {
    _uiState.update { currentState ->
      val autoSpecies = currentState.selectedSpecies ?: currentState.detectedSpecies ?: Species.DOG
      currentState.copy(
          selectedAlertId = null,
          isNoMatchingAlertSelected = true,
          selectedSpecies = autoSpecies,
      )
    }
    refreshNearbyAlertOptions()
  }

  /** Adds [activity] to the sighting's activities, or removes it if it was already chosen. */
  fun toggleActivity(activity: AnimalActivity) {
    _uiState.update { it.copy(activities = it.activities.toggled(activity)) }
  }

  /**
   * Adds [condition] to the sighting's conditions, or removes it if it was already chosen.
   * [AnimalCondition.LOOKS_FINE] excludes every other condition: choosing it clears the others, and
   * choosing another one removes it.
   */
  fun toggleCondition(condition: AnimalCondition) {
    _uiState.update { state ->
      val toggled = state.conditions.toggled(condition)
      val conditions =
          when {
            condition !in toggled -> toggled
            condition == AnimalCondition.LOOKS_FINE -> setOf(AnimalCondition.LOOKS_FINE)
            else -> toggled - AnimalCondition.LOOKS_FINE
          }
      state.copy(conditions = conditions)
    }
  }

  /** Adds [reaction] to the sighting's reactions, or removes it if it was already chosen. */
  fun toggleReaction(reaction: AnimalReaction) {
    _uiState.update { it.copy(reactions = it.reactions.toggled(reaction)) }
  }

  /** Chooses [placeType]; choosing the one already chosen clears it back to unknown. */
  fun selectPlaceType(placeType: PlaceType) {
    _uiState.update {
      it.copy(placeType = if (it.placeType == placeType) PlaceType.UNKNOWN else placeType)
    }
  }

  /** Chooses [collar]; choosing the one already chosen clears it back to unknown. */
  fun selectCollar(collar: CollarStatus) {
    _uiState.update { it.copy(collar = if (it.collar == collar) CollarStatus.UNKNOWN else collar) }
  }

  /** This set with [value] added, or without it if it was already in it. */
  private fun <T> Set<T>.toggled(value: T): Set<T> =
      if (value in this) this - value else this + value

  fun clearErrorMessage() {
    _uiState.update { it.copy(errorMessage = null) }
  }

  fun submitSighting() {
    val currentUser = authRepository.currentUser.value
    if (currentUser == null) {
      _uiState.update { it.copy(errorMessage = "You must be signed in to submit a sighting") }
      return
    }

    val currentState = _uiState.value
    if (!currentState.canSubmit) {
      _uiState.update { it.copy(errorMessage = "Species and position are required") }
      return
    }

    val species = currentState.selectedSpecies ?: return
    val location = currentState.location ?: return

    _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

    viewModelScope.launch {
      try {
        val online = networkMonitor?.isOnline() ?: true
        _uiState.update { it.copy(isOffline = !online) }
        val sightingId = sightingRepository.getNewId()
        val reporterId = currentUser.uid
        val nowMillis = now()
        val alertId =
            if (currentState.isNoMatchingAlertSelected) null else currentState.selectedAlertId
        val plausibilitySighting =
            Sighting(
                id = sightingId,
                reporterId = reporterId,
                location = location,
                seenAtMillis = nowMillis,
                species = species,
                alertId = alertId,
                headingDegrees = currentState.direction,
                activities = currentState.activities,
                conditions = currentState.conditions,
                reactions = currentState.reactions,
                placeType = currentState.placeType,
                collar = currentState.collar,
            )

        // Checked before the photo is uploaded, so nothing is uploaded for a refused sighting
        val refusal = alertId?.let {
          implausibilityMessage(plausibilitySighting, it, nowMillis, online)
        }
        if (refusal != null) {
          _uiState.update { it.copy(isSubmitting = false, errorMessage = refusal) }
          return@launch
        }

        // Offline: keep it on the phone with its local photo, it is sent once the network is back
        if (!online) {
          queue(plausibilitySighting, currentState.photoUri)
          return@launch
        }

        var uploadedPhotoUrl: String? = null
        val photoUri = currentState.photoUri
        if (photoUri != null) {
          val uploadResult =
              photoRepository.uploadPetPhoto(
                  ownerId = reporterId,
                  petId = sightingId,
                  localUri = photoUri,
              )

          // With an alert, the photo is optional: if it can't be uploaded, the sighting is still
          // sent without it
          uploadedPhotoUrl = uploadResult.getOrNull()
        }

        // Without an alert, the photo is required: don't send the sighting without it
        if (alertId == null && uploadedPhotoUrl == null) {
          _uiState.update { it.copy(isSubmitting = false, errorMessage = PHOTO_UPLOAD_FAILED) }
          return@launch
        }

        val sighting = plausibilitySighting.copy(photoUrl = uploadedPhotoUrl)

        val reportResult = sightingRepository.reportSighting(sighting)

        if (reportResult.isSuccess) {
          _uiState.update { it.copy(isSubmitting = false, submitted = true) }
        } else {
          _uiState.update {
            it.copy(
                isSubmitting = false,
                errorMessage = reportResult.exceptionOrNull()?.message ?: "Sighting report failed",
            )
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.update {
          it.copy(isSubmitting = false, errorMessage = e.message ?: "Unexpected error")
        }
      }
    }
  }

  /**
   * Queues [sighting] with the photo at [photoUri] in [pendingSightingRepository], and tells the
   * user it will be sent later, or that it couldn't be saved.
   */
  private suspend fun queue(sighting: Sighting, photoUri: String?) {
    val repository = pendingSightingRepository
    val queued = repository != null && repository.add(sighting, photoUri).isSuccess
    _uiState.update {
      if (queued) it.copy(isSubmitting = false, savedOffline = true)
      else it.copy(isSubmitting = false, errorMessage = OFFLINE_SAVE_FAILED)
    }
  }

  /**
   * Loads the alert [alertId] and checks that [sighting] can be a sighting of its pet. Returns why
   * it can't, one line per issue, or null when it is plausible. When the alert can't be loaded, a
   * sighting is refused [online], as nothing could then be checked; offline it is let through, and
   * the server checks it when the queue is sent.
   */
  private suspend fun implausibilityMessage(
      sighting: Sighting,
      alertId: String,
      nowMillis: Long,
      online: Boolean,
  ): String? {
    val alert =
        alertRepository?.getAlertById(alertId)?.getOrNull()
            ?: return if (online) ALERT_NOT_LOADED else null
    val issues = checkPlausibility(sighting, alert, nowMillis)
    return if (issues.isEmpty()) null else issues.joinToString("\n") { it.message() }
  }

  /** What the user is told about each [PlausibilityIssue]. */
  private fun PlausibilityIssue.message(): String =
      when (this) {
        PlausibilityIssue.SEEN_IN_THE_FUTURE -> SEEN_IN_THE_FUTURE_MESSAGE
        PlausibilityIssue.SEEN_BEFORE_LOST -> SEEN_BEFORE_LOST_MESSAGE
        PlausibilityIssue.TOO_FAR -> TOO_FAR_MESSAGE
        PlausibilityIssue.OWN_ALERT -> OWN_ALERT_MESSAGE
      }

  companion object {
    const val ALERT_NOT_LOADED = "Couldn't check this alert. Try again."
    const val PHOTO_UPLOAD_FAILED = "Couldn't upload your photo. Try again."
    const val OFFLINE_SAVE_FAILED = "Couldn't save your sighting on the phone. Try again."
    const val SEEN_IN_THE_FUTURE_MESSAGE = "The time of this sighting is in the future."
    const val SEEN_BEFORE_LOST_MESSAGE = "This pet was seen before it was lost."
    const val TOO_FAR_MESSAGE = "This is too far from where the pet was lost for the time elapsed."
    const val OWN_ALERT_MESSAGE = "You can't report a sighting of your own pet."

    /** Radius of the alerts offered as "Which pet did you see?", in kilometres. */
    const val NEARBY_RADIUS_KM = 5.0
    private const val MAX_NEARBY_ALERTS = 5

    val Factory: ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val application = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
            return ReportSightingViewModel(
                sightingRepository = SightingRepositoryFirestore(),
                photoRepository = PhotoRepositoryFirestore(application),
                locationRepository = LocationRepositoryProvider.create(application),
                authRepository = AuthRepositoryProvider.authRepository,
                alertRepository = AlertRepositoryFirestore(),
                animalDetector = AnimalDetectorMlKit(application),
                // TODO(#444): PendingSightingRepositoryProvider.get(application) once it is merged
                pendingSightingRepository = null,
                networkMonitor = NetworkMonitorAndroid(application),
            )
                as T
          }
        }
  }
}
