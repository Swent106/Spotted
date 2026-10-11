// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.PetRepository
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertRepository
import com.android.spotted.model.alert.AlertStatus
import com.android.spotted.model.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the "Report a pet missing" screen.
 *
 * The owner picks one of their pets and the place where they last saw it, then publishes an [Alert]
 * so that nearby users can help. The time of the alert is set automatically from [now].
 *
 * The UI only observes [uiState] and calls the public functions; it never changes the state
 * directly. When [ReportMissingUiState.published] becomes true, the UI navigates away.
 *
 * @property alertRepository where the alert is published.
 * @property petRepository where the owner's pets are loaded from.
 * @property authRepository gives the signed-in owner.
 * @property now current time in epoch millis; injected so that tests control it.
 */
class ReportMissingViewModel(
    private val alertRepository: AlertRepository,
    private val petRepository: PetRepository,
    private val authRepository: AuthRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ReportMissingUiState())
  val uiState: StateFlow<ReportMissingUiState> = _uiState.asStateFlow()

  init {
    loadPets()
  }

  /** Loads the signed-in owner's pets. If the owner has exactly one pet, it is preselected. */
  fun loadPets() {
    val owner = authRepository.currentUser.value
    if (owner == null) {
      _uiState.update { it.copy(errorMessage = NOT_SIGNED_IN) }
      return
    }
    _uiState.update { it.copy(isLoadingPets = true, errorMessage = null) }
    viewModelScope.launch {
      petRepository
          .getPetsByOwner(owner.uid)
          .fold(
              onSuccess = { pets ->
                _uiState.update {
                  it.copy(
                      pets = pets,
                      selectedPetId = pets.singleOrNull()?.id,
                      isLoadingPets = false,
                  )
                }
              },
              onFailure = {
                _uiState.update { it.copy(isLoadingPets = false, errorMessage = LOAD_PETS_FAILED) }
              },
          )
    }
  }

  /** Selects the pet that is missing. Ids that are not among the owner's pets are ignored. */
  fun selectPet(petId: String) {
    if (_uiState.value.pets.none { it.id == petId }) return
    _uiState.update { it.copy(selectedPetId = petId) }
  }

  /** Sets the last known location of the pet, as chosen on the map picker. */
  fun setLocation(location: Location) {
    _uiState.update { it.copy(location = location) }
  }

  /** Clears the error message in the UI state. */
  fun clearErrorMsg() {
    _uiState.update { it.copy(errorMessage = null) }
  }

  /**
   * Publishes an alert for the selected pet at the chosen location, timestamped with [now].
   *
   * Does nothing unless [ReportMissingUiState.canPublish] is true. On failure, the form is kept so
   * that the owner can try again.
   */
  fun publish() {
    val state = _uiState.value
    if (!state.canPublish) return
    val pet = state.selectedPet ?: return
    val location = state.location ?: return
    val owner = authRepository.currentUser.value
    if (owner == null) {
      _uiState.update { it.copy(errorMessage = NOT_SIGNED_IN) }
      return
    }

    _uiState.update { it.copy(isPublishing = true, errorMessage = null) }
    viewModelScope.launch {
      val alert =
          Alert(
              id = alertRepository.getNewId(),
              petId = pet.id,
              ownerId = owner.uid,
              lastKnownLocation = location,
              lostAtMillis = now(),
              status = AlertStatus.OPEN,
              petName = pet.name,
              petSpecies = pet.species,
              petPhotoUrl = pet.photoUrl,
              petAllergies = pet.allergies,
              petBehaviors = pet.behaviors,
          )
      alertRepository
          .publishAlert(alert)
          .fold(
              onSuccess = { _uiState.update { it.copy(isPublishing = false, published = true) } },
              onFailure = {
                _uiState.update { it.copy(isPublishing = false, errorMessage = PUBLISH_FAILED) }
              },
          )
    }
  }

  companion object {
    const val NOT_SIGNED_IN = "You need to be signed in to report a pet missing."
    const val LOAD_PETS_FAILED = "Couldn't load your pets. Please try again."
    const val PUBLISH_FAILED = "Couldn't publish the alert. Please try again."
  }
}
