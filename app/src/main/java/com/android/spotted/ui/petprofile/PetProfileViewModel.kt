// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.petprofile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.spotted.data.Pet.PetRepositoryFirestore
import com.android.spotted.data.photo.PhotoRepositoryFirestore
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepository
import com.android.spotted.model.Pet.ageInYears
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PetProfileUiState(
    val pet: Pet? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    /** Age of [pet] in whole years, or null when its birth year is unknown. */
    val ageYears: Int? = null,
)

/** @property now the current time in epoch millis, used to compute the pet's age. */
class PetProfileViewModel(
    private val petRepository: PetRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

  private val _uiState = MutableStateFlow(PetProfileUiState())
  val uiState: StateFlow<PetProfileUiState> = _uiState.asStateFlow()

  fun loadPet(petId: String) {
    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
    viewModelScope.launch {
      petRepository
          .getPet(petId)
          .fold(
              onSuccess = { pet ->
                _uiState.update {
                  it.copy(
                      pet = pet,
                      isLoading = false,
                      ageYears = ageInYears(pet.birthYear, now()),
                  )
                }
              },
              onFailure = { failure ->
                _uiState.update {
                  it.copy(
                      pet = null,
                      ageYears = null,
                      isLoading = false,
                      errorMessage = failure.localizedMessage ?: "Unable to load pet",
                  )
                }
              },
          )
    }
  }

  companion object {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
        PetProfileViewModel(
            PetRepositoryFirestore(photoRepository = PhotoRepositoryFirestore(application))
        )
      }
    }
  }
}
