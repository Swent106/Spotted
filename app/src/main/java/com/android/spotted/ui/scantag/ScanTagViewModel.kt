// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.scantag

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.spotted.data.Pet.PetRepositoryFirestore
import com.android.spotted.data.alert.AlertRepositoryFirestore
import com.android.spotted.data.photo.PhotoRepositoryFirestore
import com.android.spotted.data.tag.PetTagRepositoryFirestore
import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.PetRepository
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.AlertRepository
import com.android.spotted.model.tag.NfcAvailability
import com.android.spotted.model.tag.PetTagRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ScanTagStep {
  WaitingForTag,
  Loading,
  Found,
  Unknown,
  Error,
}

data class ScanTagUiState(
    val step: ScanTagStep = ScanTagStep.WaitingForTag,
    val nfcAvailability: NfcAvailability = NfcAvailability.AVAILABLE,
    val petName: String? = null,
    val photoUrl: String? = null,
    val species: Species? = null,
    val allergies: List<String> = emptyList(),
    val behaviors: Set<Behavior> = emptySet(),
    val visiblePhone: String? = null,
    val visibleEmail: String? = null,
    val openAlertId: String? = null,
)

class ScanTagViewModel(
    private val petTagRepository: PetTagRepository,
    private val petRepository: PetRepository,
    private val alertRepository: AlertRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ScanTagUiState())
  val uiState: StateFlow<ScanTagUiState> = _uiState.asStateFlow()
  private var scanJob: Job? = null

  fun onTagScanned(tagId: String) {
    scanJob?.cancel()
    _uiState.value = ScanTagUiState(step = ScanTagStep.Loading)
    scanJob = viewModelScope.launch {
      val tag =
          petTagRepository.getTag(tagId).getOrElse {
            _uiState.value = ScanTagUiState(step = ScanTagStep.Error)
            return@launch
          }
      if (tag == null) {
        _uiState.value = ScanTagUiState(step = ScanTagStep.Unknown)
        return@launch
      }

      val pet =
          petRepository.getPet(tag.petId).getOrElse { failure ->
            _uiState.value =
                ScanTagUiState(
                    step =
                        if (failure is NoSuchElementException) ScanTagStep.Unknown
                        else ScanTagStep.Error
                )
            return@launch
          }

      val openAlert =
          alertRepository
              .getOpenAlertsByOwner(pet.ownerId)
              .getOrElse {
                _uiState.value = ScanTagUiState(step = ScanTagStep.Error)
                return@launch
              }
              .firstOrNull { it.petId == pet.id }

      _uiState.value =
          ScanTagUiState(
              step = ScanTagStep.Found,
              petName = pet.name,
              photoUrl = pet.photoUrl,
              species = pet.species,
              allergies = pet.allergies,
              behaviors = pet.behaviors,
              visiblePhone = if (tag.showPhone) tag.contactPhone else null,
              visibleEmail = if (tag.showEmail) tag.contactEmail else null,
              openAlertId = openAlert?.id,
          )
    }
  }

  fun resetForRetry() {
    scanJob?.cancel()
    _uiState.value = ScanTagUiState()
  }

  companion object {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
        ScanTagViewModel(
            petTagRepository = PetTagRepositoryFirestore(),
            petRepository =
                PetRepositoryFirestore(photoRepository = PhotoRepositoryFirestore(application)),
            alertRepository = AlertRepositoryFirestore(),
        )
      }
    }
  }
}
