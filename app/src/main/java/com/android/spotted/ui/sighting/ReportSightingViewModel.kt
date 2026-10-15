package com.android.spotted.ui.sighting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.spotted.data.photo.PhotoRepositoryFirebaseStorage
import com.android.spotted.data.sighting.SightingRepositoryFirestore
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
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

          if (uploadResult.isFailure) {
            _uiState.update {
              it.copy(
                  isSubmitting = false,
                  errorMessage = uploadResult.exceptionOrNull()?.message ?: "Photo upload failed",
              )
            }
            return@launch
          }
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
    val Factory: ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReportSightingViewModel(
                sightingRepository = SightingRepositoryFirestore(),
                photoRepository = PhotoRepositoryFirebaseStorage(),
                authRepository = AuthRepositoryProvider.authRepository,
            )
                as T
          }
        }
  }
}
