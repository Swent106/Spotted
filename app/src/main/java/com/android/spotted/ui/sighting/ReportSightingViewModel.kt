package com.android.spotted.ui.sighting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.auth.AuthRepositoryProvider
import com.android.spotted.model.photo.FakePhotoRepository
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
    private val now: () -> Long = { System.currentTimeMillis() },
    private val reporterIdProvider: () -> String = {
      AuthRepositoryProvider.authRepository.currentUser.value?.uid ?: "anonymous_reporter"
    },
) : ViewModel() {

  private val _uiState = MutableStateFlow(ReportSightingUiState())
  val uiState: StateFlow<ReportSightingUiState> = _uiState.asStateFlow()

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
    _uiState.update { it.copy(selectedAlertId = alertId, isNoMatchingAlertSelected = false) }
  }

  fun selectNoMatchingAlert() {
    _uiState.update { it.copy(selectedAlertId = null, isNoMatchingAlertSelected = true) }
  }

  fun clearErrorMessage() {
    _uiState.update { it.copy(errorMessage = null) }
  }

  fun submitSighting() {
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
        val reporterId = reporterIdProvider()

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
                sightingRepository =
                    object : SightingRepository {
                      override fun getNewId(): String = "sighting_id"

                      override suspend fun reportSighting(sighting: Sighting): Result<Unit> =
                          Result.success(Unit)

                      override suspend fun getSightingsForAlert(
                          alertId: String
                      ): Result<List<Sighting>> = Result.success(emptyList())
                    },
                photoRepository = FakePhotoRepository(),
            )
                as T
          }
        }
  }
}
