package com.android.spotted.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.spotted.data.user.UserRepositoryFirestore
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.AuthRepositoryProvider
import com.android.spotted.model.user.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Helper profile: the signed-in user's name, area and alert radius.
 *
 * @property userRepository reads the user's profile.
 * @property authRepository gives the signed-in user.
 */
class HelperProfileViewModel(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
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

  companion object {
    const val LOAD_FAILED = "Couldn't load your profile."

    val Factory: ViewModelProvider.Factory = viewModelFactory {
      initializer {
        HelperProfileViewModel(
            userRepository = UserRepositoryFirestore(),
            authRepository = AuthRepositoryProvider.authRepository,
        )
      }
    }
  }
}
