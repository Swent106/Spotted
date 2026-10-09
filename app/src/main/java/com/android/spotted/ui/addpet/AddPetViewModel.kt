package com.android.spotted.ui.addpet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepository
import com.android.spotted.model.photo.PhotoRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AddPetViewModel(
    private val petRepository: PetRepository,
    private val photoRepository: PhotoRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(AddPetUiState())
  val uiState: StateFlow<AddPetUiState> = _uiState.asStateFlow()

  fun savePet(
      ownerId: String,
      name: String,
      species: com.android.spotted.model.Pet.Species,
      breed: String = "",
      photoUri: String? = null,
  ) {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
      try {
        val petId = petRepository.getNewId().ifEmpty { UUID.randomUUID().toString() }

        var photoUrl: String? = null
        if (photoUri != null) {
          val uploadResult = photoRepository.uploadPetPhoto(ownerId, petId, photoUri)
          if (uploadResult.isSuccess) {
            photoUrl = uploadResult.getOrNull()
          } else {
            _uiState.value =
                _uiState.value.copy(
                    isSaving = false,
                    errorMessage =
                        uploadResult.exceptionOrNull()?.message ?: "Error uploading photo",
                )
            return@launch
          }
        }

        val pet =
            Pet(
                id = petId,
                ownerId = ownerId,
                name = name,
                species = species,
                breed = breed,
                photoUrl = photoUrl,
            )

        val result = petRepository.addPet(pet)
        if (result.isSuccess) {
          _uiState.value = _uiState.value.copy(isSaving = false, saveSuccess = true)
        } else {
          _uiState.value =
              _uiState.value.copy(
                  isSaving = false,
                  errorMessage = result.exceptionOrNull()?.message ?: "Error saving pet",
              )
        }
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = e.message)
      }
    }
  }
}

data class AddPetUiState(
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null,
)
