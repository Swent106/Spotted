// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.addpets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.spotted.R
import com.android.spotted.data.Pet.PetRepositoryFirestore
import com.android.spotted.data.photo.PhotoRepositoryFirestore
import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepository
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.auth.AuthRepositoryProvider
import com.android.spotted.model.auth.User
import com.android.spotted.model.photo.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the onboarding form for adding pets. Pets are saved one at a time: [savePet] saves the
 * current pet and clears the form for the next one, [finish] saves the current pet (if any) and
 * ends the step, and [skip] ends the step without saving.
 */
class AddPetsViewModel(
    private val user: User?,
    private val petRepository: PetRepository,
    private val photoRepository: PhotoRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(AddPetsUiState())
  val uiState: StateFlow<AddPetsUiState> = _uiState.asStateFlow()

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

  fun onPhotoPickerError() {
    _uiState.update {
      it.copy(errorMessage = AddPetsMessage.Resource(R.string.photo_camera_unavailable))
    }
  }

  fun onDescriptionChanged(description: String) {
    _uiState.update { it.copy(description = description, errorMessage = null) }
  }

  fun onAgeChanged(age: String) {
    _uiState.update { it.copy(age = age, errorMessage = null) }
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

  /**
   * Saves the current pet and clears the form so another pet can be added. Does nothing unless
   * [AddPetsUiState.canSave] is true.
   */
  fun savePet() {
    save(finishAfterSaving = false)
  }

  /**
   * Saves the current pet if the form was started, then ends the step. Does nothing unless
   * [AddPetsUiState.canFinish] is true.
   */
  fun finish() {
    val state = _uiState.value
    if (!state.canFinish) return
    if (state.hasFormInput) {
      save(finishAfterSaving = true)
    } else {
      _uiState.update { it.copy(isDone = true) }
    }
  }

  /** Ends the step without saving the current pet. */
  fun skip() {
    if (_uiState.value.isSaving) return
    _uiState.update { it.copy(isDone = true) }
  }

  /**
   * Called by the screen once it has navigated away, so coming back to this screen does not end the
   * step again.
   */
  fun onDoneHandled() {
    _uiState.update { it.copy(isDone = false) }
  }

  private fun save(finishAfterSaving: Boolean) {
    val state = _uiState.value
    if (!state.canSave) return
    val species = state.species ?: return
    // Already added with "+": saving it again would create the same pet twice
    if (
        state.addedPets.any {
          it.name.equals(state.name.trim(), ignoreCase = true) && it.species == species
        }
    ) {
      _uiState.update { AddPetsUiState(addedPets = it.addedPets, isDone = finishAfterSaving) }
      return
    }
    val ownerId = user?.uid
    if (ownerId == null) {
      _uiState.update {
        it.copy(
            needsSignIn = true,
            errorMessage = AddPetsMessage.Resource(R.string.add_pets_not_signed_in),
        )
      }
      return
    }

    _uiState.update { it.copy(isSaving = true, errorMessage = null) }
    viewModelScope.launch {
      val petId = petRepository.getNewId()
      // The photo is optional: if it can't be uploaded (e.g. Storage unavailable), the pet is
      // still saved, without it, and the user is told
      val photoUpload = state.photoUri?.let { photoRepository.uploadPetPhoto(ownerId, petId, it) }
      val photoUrl = photoUpload?.getOrNull()
      val photoFailed = photoUpload?.isFailure == true
      val pet =
          Pet(
              id = petId,
              ownerId = ownerId,
              name = state.name.trim(),
              species = species,
              breed = state.breed.trim(),
              photoUrl = photoUrl,
              allergies = state.allergies,
              behaviors = state.behaviors,
              note = state.description.trim(),
          )
      petRepository
          .addPet(pet)
          .fold(
              onSuccess = {
                _uiState.update {
                  AddPetsUiState(
                      addedPets = it.addedPets + pet,
                      isDone = finishAfterSaving,
                      errorMessage =
                          if (photoFailed) {
                            AddPetsMessage.Resource(R.string.add_pets_photo_upload_error)
                          } else {
                            null
                          },
                  )
                }
              },
              onFailure = { failure -> showError(failure) },
          )
    }
  }

  private fun showError(failure: Throwable) {
    _uiState.update {
      it.copy(
          isSaving = false,
          errorMessage =
              failure.localizedMessage?.let(AddPetsMessage::Text)
                  ?: AddPetsMessage.Resource(R.string.add_pets_save_error),
      )
    }
  }

  companion object {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
        AddPetsViewModel(
            user = AuthRepositoryProvider.authRepository.currentUser.value,
            petRepository = PetRepositoryFirestore(),
            photoRepository = PhotoRepositoryFirestore(application),
        )
      }
    }
  }
}
