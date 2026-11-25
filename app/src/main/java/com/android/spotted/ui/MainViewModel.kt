// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.android.spotted.data.network.NetworkMonitorAndroid
import com.android.spotted.data.user.UserRepositoryFirestore
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.AuthRepositoryProvider
import com.android.spotted.model.network.NetworkMonitor
import com.android.spotted.model.user.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

  private val _uiState = MutableStateFlow(MainUiState())
  val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

  /** Collects [NetworkMonitor.onlineChanges] while the app is in the foreground; null otherwise. */
  private var networkJob: Job? = null

  init {
    viewModelScope.launch {
      authRepository.currentUser.collect { user ->
        val destination =
            if (user == null) {
              StartDestination.SIGNIN
            } else {
              val profileResult = userRepository.getUser(user.uid)
              if (profileResult.isSuccess && profileResult.getOrNull() != null) {
                StartDestination.HOME
              } else {
                StartDestination.PERSONAL_INFO
              }
            }
        _uiState.update { it.copy(destination = destination) }
      }
    }
  }

  /** Starts watching the connection. Called when the app comes to the foreground. */
  fun startWatchingNetwork() {
    if (networkJob != null) return
    networkJob = viewModelScope.launch {
      networkMonitor.onlineChanges.collect { online ->
        _uiState.update { it.copy(isOnline = online) }
      }
    }
  }

  /**
   * Stops watching the connection, so the platform is not listened to in the background. The last
   * known value is kept until watching starts again.
   */
  fun stopWatchingNetwork() {
    networkJob?.cancel()
    networkJob = null
  }

  companion object {
    val Factory: ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val application = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
            return MainViewModel(
                authRepository = AuthRepositoryProvider.authRepository,
                userRepository = UserRepositoryFirestore(),
                networkMonitor = NetworkMonitorAndroid(application),
            )
                as T
          }
        }
  }
}
