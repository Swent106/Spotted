// Portions of this code were generated with the help of Claude (Anthropic).
// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.editpet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.spotted.R
import com.android.spotted.data.Pet.PetRepositoryFirestore
import com.android.spotted.data.alert.AlertRepositoryFirestore
import com.android.spotted.data.photo.PhotoRepositoryFirestore
import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepository
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.AlertRepository
import com.android.spotted.model.photo.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditPetViewModel(
    private val petId: String,
    private val petRepository: PetRepository,
    private val alertRepository: AlertRepository,
    private val photoRepository: PhotoRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(EditPetUiState())
  val uiState: StateFlow<EditPetUiState> = _uiState.asStateFlow()
  private var originalPet: Pet? = null

  init {
    loadPet()
  }

  fun onNameChanged(name: String) {
    _uiState.update { it.copy(name = name, errorMessage = null) }
  }

  fun onSpeciesChanged(species: Species) {
    _uiState.update { it.copy(species = species, errorMessage = null) }
  }

  fun onBreedChanged(breed: String) {
    _uiState.update { it.copy(breed = breed, errorMessage = null) }
  }

  fun onPhotoChanged(photoUri: String?) {
    _uiState.update { it.copy(photoUri = photoUri, errorMessage = null) }
  }

  fun onDescriptionChanged(description: String) {
    _uiState.update { it.copy(description = description, errorMessage = null) }
  }

  fun onAllergiesChanged(allergies: List<String>) {
    _uiState.update { it.copy(allergies = allergies, errorMessage = null) }
  }

  fun onBehaviorToggled(behavior: Behavior) {
    _uiState.update {
      val behaviors =
          if (behavior in it.behaviors) it.behaviors - behavior else it.behaviors + behavior
      it.copy(behaviors = behaviors, errorMessage = null)
    }
  }

  /** The photo picker could not open the camera: tells the user to pick from the gallery. */
  fun onPhotoPickerError() {
    _uiState.update { it.copy(errorMessage = R.string.photo_camera_unavailable) }
  }

  /**
   * Saves the form when [EditPetUiState.canSave]. Uploads the photo only if it changed, updates the
   * pet, then deletes the stored photo if it was removed. Ends with `saved = true`, or with an
   * [EditPetUiState.errorMessage] and the form kept; saving again then retries every step.
   */
  fun save() {
    val state = _uiState.value
    val pet = originalPet ?: return
    if (!state.canSave) return

    _uiState.update { it.copy(saving = true, errorMessage = null) }
    viewModelScope.launch {
      val photoUrl =
          if (state.photoUri == pet.photoUrl) {
            pet.photoUrl
          } else if (state.photoUri == null) {
            null
          } else {
            val upload = photoRepository.uploadPetPhoto(pet.ownerId, pet.id, state.photoUri)
            if (upload.isFailure) {
              _uiState.update {
                it.copy(saving = false, errorMessage = R.string.edit_pet_photo_upload_error)
              }
              return@launch
            }
            upload.getOrThrow()
          }

      val updatedPet =
          pet.copy(
              name = state.name.trim(),
              species = state.species ?: pet.species,
              breed = state.breed.trim(),
              photoUrl = photoUrl,
              allergies = state.allergies,
              behaviors = state.behaviors,
              note = state.description.trim(),
          )
      if (petRepository.updatePet(updatedPet).isFailure) {
        _uiState.update { it.copy(saving = false, errorMessage = R.string.edit_pet_save_error) }
        return@launch
      }
      // A removed photo is deleted too, or any signed-in user could still read it. On failure,
      // originalPet keeps the old photo, so saving again retries the deletion.
      val photoRemoved = pet.photoUrl != null && photoUrl == null
      if (photoRemoved && photoRepository.deletePetPhoto(pet.ownerId, pet.id).isFailure) {
        _uiState.update {
          it.copy(saving = false, errorMessage = R.string.edit_pet_photo_delete_error)
        }
        return@launch
      }
      originalPet = updatedPet
      _uiState.update {
        it.copy(
            name = updatedPet.name,
            breed = updatedPet.breed,
            photoUri = updatedPet.photoUrl,
            description = updatedPet.note,
            saving = false,
            saved = true,
            errorMessage = null,
        )
      }
    }
  }

  /**
   * Deletes the pet, unless it has an open alert ([R.string.edit_pet_open_alert_error]). Ends with
   * `deleted = true`, or with an [EditPetUiState.errorMessage] and the pet kept.
   */
  fun delete() {
    val pet = originalPet ?: return
    if (_uiState.value.saving || _uiState.value.deleted) return

    _uiState.update { it.copy(saving = true, errorMessage = null) }
    viewModelScope.launch {
      val alerts =
          alertRepository.getOpenAlertsByOwner(pet.ownerId).getOrElse {
            _uiState.update {
              it.copy(saving = false, errorMessage = R.string.edit_pet_delete_error)
            }
            return@launch
          }
      if (alerts.any { it.petId == pet.id }) {
        _uiState.update {
          it.copy(saving = false, errorMessage = R.string.edit_pet_open_alert_error)
        }
        return@launch
      }

      petRepository
          .deletePet(pet.id)
          .fold(
              onSuccess = {
                _uiState.update { it.copy(saving = false, deleted = true, errorMessage = null) }
              },
              onFailure = {
                _uiState.update {
                  it.copy(saving = false, errorMessage = R.string.edit_pet_delete_error)
                }
              },
          )
    }
  }

  private fun loadPet() {
    viewModelScope.launch {
      petRepository
          .getPet(petId)
          .fold(
              onSuccess = { pet ->
                originalPet = pet
                _uiState.update {
                  it.copy(
                      name = pet.name,
                      species = pet.species,
                      breed = pet.breed,
                      photoUri = pet.photoUrl,
                      description = pet.note,
                      allergies = pet.allergies,
                      behaviors = pet.behaviors,
                      isLoading = false,
                      errorMessage = null,
                  )
                }
              },
              onFailure = {
                _uiState.update {
                  it.copy(
                      isLoading = false,
                      loadFailed = true,
                      errorMessage = R.string.edit_pet_load_error,
                  )
                }
              },
          )
    }
  }

  companion object {
    fun factory(petId: String): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
        val photoRepository = PhotoRepositoryFirestore(application)
        EditPetViewModel(
            petId = petId,
            petRepository = PetRepositoryFirestore(photoRepository = photoRepository),
            alertRepository = AlertRepositoryFirestore(),
            photoRepository = photoRepository,
        )
      }
    }
  }
}
