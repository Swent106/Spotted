package com.android.spotted.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.AuthRepositoryProvider
import com.android.spotted.model.user.FakeUserRepository
import com.android.spotted.model.user.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(StartDestination.SIGNIN)
  val uiState: StateFlow<StartDestination> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      authRepository.currentUser.collect { user ->
        if (user == null) {
          _uiState.value = StartDestination.SIGNIN
        } else {
          val profileResult = userRepository.getUser(user.uid)
          if (profileResult.isSuccess && profileResult.getOrNull() != null) {
            _uiState.value = StartDestination.HOME
          } else {
            _uiState.value = StartDestination.PERSONAL_INFO
          }
        }
      }
    }
  }

  companion object {
    val Factory: ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(
                authRepository = AuthRepositoryProvider.authRepository,
                userRepository = FakeUserRepository(),
            )
                as T
          }
        }
  }
}
