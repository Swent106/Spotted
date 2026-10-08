package com.android.spotted.ui.petprofile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PetProfileViewModel(private val petRepository: PetRepository) : ViewModel() {

  private val _pet = MutableStateFlow<Pet?>(null)
  val pet: StateFlow<Pet?> = _pet.asStateFlow()

  fun loadPet(petId: String) {
    viewModelScope.launch {
      val result = petRepository.getPet(petId)
      if (result.isSuccess) {
        _pet.value = result.getOrNull()
      }
    }
  }
}
