package com.android.spotted.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.spotted.data.Pet.PetRepositoryFirestore
import com.android.spotted.data.alert.AlertRepositoryFirestore
import com.android.spotted.data.user.UserRepositoryFirestore
import com.android.spotted.model.Pet.PetRepository
import com.android.spotted.model.alert.AlertRepository
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.AuthRepositoryProvider
import com.android.spotted.model.user.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Helper profile: the signed-in user's name and area, and the account actions.
 *
 * Signing out or deleting the account clears the signed-in user, so MainViewModel sends the app
 * back to Sign in; this ViewModel does not navigate itself.
 *
 * @property userRepository reads and deletes the user's profile.
 * @property authRepository gives the signed-in user, signs out and deletes the account.
 * @property petRepository deletes the user's pets with the account.
 * @property alertRepository deletes the user's alerts with the account.
 */
class HelperProfileViewModel(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val petRepository: PetRepository,
    private val alertRepository: AlertRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(HelperProfileUiState())
  val uiState: StateFlow<HelperProfileUiState> = _uiState.asStateFlow()

  /**
   * Loads the signed-in user's profile. The screen calls it each time it is shown, so changes made
   * in the profile form appear when coming back.
   */
  fun load() {
    val user = authRepository.currentUser.value ?: return
    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
    viewModelScope.launch {
      userRepository
          .getUser(user.uid)
          .fold(
              onSuccess = { profile ->
                _uiState.update {
                  it.copy(
                      name = profile.name,
                      homeArea = profile.homeArea,
                      alertRadiusKm = profile.alertRadiusKm,
                      isLoading = false,
                  )
                }
              },
              onFailure = {
                _uiState.update { it.copy(isLoading = false, errorMessage = LOAD_FAILED) }
              },
          )
    }
  }

  /** Signs out; the profile is kept for the next sign-in. */
  fun signOut() {
    if (_uiState.value.isLeaving) return
    _uiState.update { it.copy(isLeaving = true, errorMessage = null) }
    viewModelScope.launch {
      authRepository.signOut().onFailure {
        _uiState.update { it.copy(isLeaving = false, errorMessage = SIGN_OUT_FAILED) }
      }
    }
  }

  /**
   * Deletes the user's alerts, pets and profile, then their account, so the next sign-in starts
   * from a new, empty profile. If Firebase refuses to delete the account (signed in too long ago),
   * the profile is already gone, so the user is signed out instead: the next sign-in still asks for
   * a new profile.
   */
  fun deleteAccount() {
    if (_uiState.value.isLeaving) return
    val user = authRepository.currentUser.value ?: return
    _uiState.update { it.copy(isLeaving = true, errorMessage = null) }
    viewModelScope.launch {
      // Alerts and pets first: otherwise they would come back with the next profile of this
      // Google account. Alerts before pets, as an alert points to its pet
      val dataDeleted =
          alertRepository.deleteAlertsByOwner(user.uid).isSuccess &&
              deleteAllPetsOf(user.uid) &&
              userRepository.deleteUser(user.uid).isSuccess
      if (!dataDeleted) {
        _uiState.update { it.copy(isLeaving = false, errorMessage = DELETE_FAILED) }
        return@launch
      }
      authRepository.deleteAccount().onFailure {
        authRepository.signOut().onFailure {
          _uiState.update { it.copy(isLeaving = false, errorMessage = SIGN_OUT_FAILED) }
        }
      }
    }
  }

  /** Deletes every pet of [uid]; false if one of them couldn't be loaded or deleted. */
  private suspend fun deleteAllPetsOf(uid: String): Boolean {
    val pets =
        petRepository.getPetsByOwner(uid).getOrElse {
          return false
        }
    return pets.all { petRepository.deletePet(it.id).isSuccess }
  }

  companion object {
    const val LOAD_FAILED = "Couldn't load your profile."
    const val SIGN_OUT_FAILED = "Couldn't sign you out. Please try again."
    const val DELETE_FAILED = "Couldn't delete your account. Please try again."

    val Factory: ViewModelProvider.Factory = viewModelFactory {
      initializer {
        HelperProfileViewModel(
            userRepository = UserRepositoryFirestore(),
            authRepository = AuthRepositoryProvider.authRepository,
            petRepository = PetRepositoryFirestore(),
            alertRepository = AlertRepositoryFirestore(),
        )
      }
    }
  }
}
