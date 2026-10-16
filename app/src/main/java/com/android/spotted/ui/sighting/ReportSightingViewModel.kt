package com.android.spotted.ui.sighting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.android.spotted.data.alert.AlertRepositoryFirestore
import com.android.spotted.data.location.LocationRepositoryFused
import com.android.spotted.data.photo.PhotoRepositoryFirestore
import com.android.spotted.data.sighting.SightingRepositoryFirestore
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertRepository
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.AuthRepositoryProvider
import com.android.spotted.model.location.LocationRepository
import com.android.spotted.model.photo.PhotoRepository
import com.android.spotted.model.sighting.Sighting
import com.android.spotted.model.sighting.SightingRepository
import kotlin.coroutines.cancellation.CancellationException
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
) : ViewModel() {

  private val _uiState = MutableStateFlow(ReportSightingUiState())
  val uiState: StateFlow<ReportSightingUiState> = _uiState.asStateFlow()

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
    _uiState.update { it.copy(selectedSpecies = species) }
  }

  fun setLocation(location: Location, address: String = "Location on map") {
    _uiState.update { it.copy(location = location, locationAddress = address) }
    loadNearbyAlerts(location)
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
      repository.getAlertById(alertId).getOrNull()?.let { alert -> addAlertOptions(listOf(alert)) }
      selectAlert(alertId)
    }
  }

  /** Lists the open alerts around [location], so the user can say which pet they saw. */
  private fun loadNearbyAlerts(location: Location) {
    val repository = alertRepository ?: return
    viewModelScope.launch {
      repository.getOpenAlertsNear(location, NEARBY_RADIUS_KM).onSuccess(::addAlertOptions)
    }
  }

  /** Adds [alerts] to the options, after the ones already listed, without duplicates. */
  private fun addAlertOptions(alerts: List<Alert>) {
    _uiState.update { state ->
      val known = state.nearbyAlerts.map { it.alertId }.toSet()
      val added =
          alerts
              .filter { it.id !in known }
              .map { alert ->
                NearbyAlertOption(
                    alertId = alert.id,
                    petName = alert.petName,
                    description =
                        alert.petSpecies.name.lowercase().replaceFirstChar(Char::titlecase),
                    photoUrl = alert.petPhotoUrl,
                    species = alert.petSpecies,
                )
              }
      // The selected alert now has its species, which the sighting needs
      val selectedSpecies =
          state.selectedSpecies
              ?: added.firstOrNull { it.alertId == state.selectedAlertId }?.species
      state.copy(nearbyAlerts = state.nearbyAlerts + added, selectedSpecies = selectedSpecies)
    }
  }

  fun setPhotoUri(uri: String?) {
    _uiState.update { it.copy(photoUri = uri) }
  }

  fun selectAlert(alertId: String) {
    _uiState.update { currentState ->
      val alert = currentState.nearbyAlerts.firstOrNull { it.alertId == alertId }
      val autoSpecies =
          alert?.species ?: currentState.detectedSpecies ?: currentState.selectedSpecies
      currentState.copy(
          selectedAlertId = alertId,
          isNoMatchingAlertSelected = false,
          selectedSpecies = autoSpecies,
      )
    }
  }

  fun selectNoMatchingAlert() {
    _uiState.update { currentState ->
      val autoSpecies = currentState.detectedSpecies ?: currentState.selectedSpecies ?: Species.DOG
      currentState.copy(
          selectedAlertId = null,
          isNoMatchingAlertSelected = true,
          selectedSpecies = autoSpecies,
      )
    }
  }

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
        val sightingId = sightingRepository.getNewId()
        val reporterId = currentUser.uid

        var uploadedPhotoUrl: String? = null
        val photoUri = currentState.photoUri
        if (photoUri != null) {
          val uploadResult =
              photoRepository.uploadPetPhoto(
                  ownerId = reporterId,
                  petId = sightingId,
                  localUri = photoUri,
              )

          // The photo is optional: if it can't be uploaded, the sighting is still sent without it
          uploadedPhotoUrl = uploadResult.getOrNull()
        }

        val sighting =
            Sighting(
                id = sightingId,
                reporterId = reporterId,
                location = location,
                seenAtMillis = now(),
                species = species,
                photoUrl = uploadedPhotoUrl,
                alertId =
                    if (currentState.isNoMatchingAlertSelected) null
                    else currentState.selectedAlertId,
            )

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

  companion object {
    /** Radius of the alerts offered as "Which pet did you see?", in kilometres. */
    const val NEARBY_RADIUS_KM = 5.0

    val Factory: ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val application = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
            return ReportSightingViewModel(
                sightingRepository = SightingRepositoryFirestore(),
                photoRepository = PhotoRepositoryFirestore(application),
                locationRepository = LocationRepositoryFused(application),
                authRepository = AuthRepositoryProvider.authRepository,
                alertRepository = AlertRepositoryFirestore(),
            )
                as T
          }
        }
  }
}
