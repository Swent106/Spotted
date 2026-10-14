package com.android.spotted.ui.alertdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.spotted.data.alert.AlertRepositoryFirestore
import com.android.spotted.model.alert.AlertRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AlertDetailViewModel(private val alertRepository: AlertRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(AlertDetailUiState())
  val uiState: StateFlow<AlertDetailUiState> = _uiState.asStateFlow()

  private var loadJob: Job? = null

  /** Loads the alert identified by [alertId]. */
  fun loadAlert(alertId: String) {
    loadJob?.cancel()
    _uiState.value = AlertDetailUiState(isLoading = true)
    loadJob = viewModelScope.launch {
      alertRepository
          .getAlertById(alertId)
          .fold(
              onSuccess = { alert ->
                _uiState.update {
                  it.copy(alert = alert, isLoading = false, notFound = alert == null)
                }
              },
              onFailure = { failure ->
                _uiState.update {
                  it.copy(
                      isLoading = false,
                      errorMessage = failure.localizedMessage ?: LOAD_FAILED,
                  )
                }
              },
          )
    }
  }

  companion object {
    const val LOAD_FAILED = "Couldn't load the alert. Please try again."

    val Factory: ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AlertDetailViewModel::class.java))
            return AlertDetailViewModel(AlertRepositoryFirestore()) as T
          }
        }
  }
}
