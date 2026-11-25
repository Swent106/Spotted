// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** ViewModel of the Settings screen. It has no settings yet: later tasks add them. */
class SettingsViewModel : ViewModel() {

  private val _uiState = MutableStateFlow(SettingsUiState())
  val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

  companion object {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
      initializer { SettingsViewModel() }
    }
  }
}
