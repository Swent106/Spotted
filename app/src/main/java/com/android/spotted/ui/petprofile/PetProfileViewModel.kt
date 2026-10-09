package com.android.spotted.ui.petprofile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.spotted.data.Pet.PetRepositoryFirestore
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PetProfileUiState(
    val pet: Pet? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

class PetProfileViewModel(private val petRepository: PetRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(PetProfileUiState())
  val uiState: StateFlow<PetProfileUiState> = _uiState.asStateFlow()

  fun loadPet(petId: String) {
    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
    viewModelScope.launch {
      petRepository
          .getPet(petId)
          .fold(
              onSuccess = { pet -> _uiState.update { it.copy(pet = pet, isLoading = false) } },
              onFailure = { failure ->
                _uiState.update {
                  it.copy(
                      pet = null,
                      isLoading = false,
                      errorMessage = failure.localizedMessage ?: "Unable to load pet",
                  )
                }
              },
          )
    }
  }

  companion object {
    val Factory: ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PetProfileViewModel::class.java))
            return PetProfileViewModel(PetRepositoryFirestore()) as T
          }
        }
  }
}
