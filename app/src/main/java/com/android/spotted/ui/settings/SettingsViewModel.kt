// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.AuthRepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel of the Settings screen.
 *
 * Signing out clears the signed-in user, so MainViewModel sends the app back to Sign in; this
 * ViewModel does not navigate itself.
 *
 * @property authRepository signs the user out.
 */
class SettingsViewModel(private val authRepository: AuthRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(SettingsUiState())
  val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

  /** Signs out; the profile is kept for the next sign-in. Ignored while already signing out. */
  fun signOut() {
    if (_uiState.value.isLeaving) return
    _uiState.update { it.copy(isLeaving = true, errorMessage = null) }
    viewModelScope.launch {
      authRepository.signOut().onFailure {
        _uiState.update { it.copy(isLeaving = false, errorMessage = SIGN_OUT_FAILED) }
      }
    }
  }

  companion object {
    const val SIGN_OUT_FAILED = "Couldn't sign you out. Please try again."

    val Factory: ViewModelProvider.Factory = viewModelFactory {
      initializer { SettingsViewModel(AuthRepositoryProvider.authRepository) }
    }
  }
}
