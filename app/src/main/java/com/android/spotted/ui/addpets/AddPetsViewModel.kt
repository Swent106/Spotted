package com.android.spotted.ui.addpets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.spotted.data.Pet.PetRepositoryFirestore
import com.android.spotted.data.photo.PhotoRepositoryFirebaseStorage
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

  /** Saves the current pet and clears the form so another pet can be added. */
  fun savePet() {
    save(finishAfterSaving = false)
  }

  /** Saves the current pet if the form was started, then ends the step. */
  fun finish() {
    val state = _uiState.value
    if (state.isSaving || state.isDone) return
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

  private fun save(finishAfterSaving: Boolean) {
    val state = _uiState.value
    if (state.isSaving) return
    val species = state.species
    if (!state.canSave || species == null) {
      _uiState.update { it.copy(errorMessage = MISSING_FIELDS_MESSAGE) }
      return
    }
    val ownerId = user?.uid
    if (ownerId == null) {
      _uiState.update { it.copy(errorMessage = NOT_SIGNED_IN_MESSAGE) }
      return
    }

    _uiState.update { it.copy(isSaving = true, errorMessage = null) }
    viewModelScope.launch {
      val petId = petRepository.getNewId()
      val photoUrl =
          state.photoUri?.let { photoUri ->
            photoRepository.uploadPetPhoto(ownerId, petId, photoUri).getOrElse { failure ->
              showError(failure, PHOTO_ERROR_MESSAGE)
              return@launch
            }
          }
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
                  AddPetsUiState(addedPets = it.addedPets + pet, isDone = finishAfterSaving)
                }
              },
              onFailure = { failure -> showError(failure, SAVE_ERROR_MESSAGE) },
          )
    }
  }

  private fun showError(failure: Throwable, fallback: String) {
    _uiState.update {
      it.copy(isSaving = false, errorMessage = failure.localizedMessage ?: fallback)
    }
  }

  companion object {
    const val MISSING_FIELDS_MESSAGE = "Add a name and choose an animal"
    const val NOT_SIGNED_IN_MESSAGE = "You need to be signed in to add a pet"
    const val PHOTO_ERROR_MESSAGE = "Unable to upload the photo"
    const val SAVE_ERROR_MESSAGE = "Unable to save the pet"

    val Factory: ViewModelProvider.Factory = viewModelFactory {
      initializer {
        AddPetsViewModel(
            user = AuthRepositoryProvider.authRepository.currentUser.value,
            petRepository = PetRepositoryFirestore(),
            photoRepository = PhotoRepositoryFirebaseStorage(),
        )
      }
    }
  }
}
