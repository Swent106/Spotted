// Portions of this code were generated with the help of Claude (Anthropic).
// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.myalert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.spotted.data.alert.AlertRepositoryFirestore
import com.android.spotted.data.sighting.SightingRepositoryFirestore
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertRepository
import com.android.spotted.model.alert.AlertStatus
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.AuthRepositoryProvider
import com.android.spotted.model.sighting.SightingRepository
import com.android.spotted.model.sighting.SightingStatus
import com.android.spotted.model.sighting.estimateLikelyArea
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MyAlertViewModel(
    private val alertId: String,
    private val alertRepository: AlertRepository,
    private val sightingRepository: SightingRepository,
    private val authRepository: AuthRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

  private val _uiState = MutableStateFlow(MyAlertUiState())
  val uiState: StateFlow<MyAlertUiState> = _uiState.asStateFlow()
  // Tracks the stream error so a successful snapshot clears only that error, not action errors.
  private var sightingsError: String? = null

  init {
    loadAlert()
  }

  private fun loadAlert() {
    viewModelScope.launch {
      alertRepository
          .getAlertById(alertId)
          .fold(
              onSuccess = { alert ->
                when {
                  alert == null -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = ALERT_NOT_FOUND) }
                  }
                  authRepository.currentUser.value?.uid != alert.ownerId -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = NOT_OWNER) }
                  }
                  else -> {
                    _uiState.update {
                      it.copy(
                          alert = alert,
                          isLoading = false,
                          isClosed = alert.status == AlertStatus.CLOSED,
                      )
                    }
                    observeSightings(alert)
                  }
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

  private fun observeSightings(alert: Alert) {
    viewModelScope.launch {
      sightingRepository.observeSightingsForAlert(alert.id).collect { result ->
        result.fold(
            onSuccess = { sightings ->
              _uiState.update {
                it.copy(
                    sightings = sightings,
                    likelyArea = estimateLikelyArea(sightings, alert.petSpecies, now()),
                    errorMessage = if (it.errorMessage == sightingsError) null else it.errorMessage,
                )
              }
              sightingsError = null
            },
            onFailure = { failure ->
              val message = failure.localizedMessage ?: SIGHTINGS_LOAD_FAILED
              sightingsError = message
              _uiState.update { it.copy(errorMessage = message) }
            },
        )
      }
    }
  }

  /**
   * Confirms the sighting [id], allowing it to inform the likely area. If it is newer than the
   * alert's last seen time, the alert's last seen place moves to it.
   */
  fun confirm(id: String) =
      setSightingStatus(id, SightingStatus.CONFIRMED, onUpdated = { moveLastSeenTo(id) })

  /** Dismisses the sighting [id], removing it from the likely-area estimate. */
  fun dismiss(id: String) = setSightingStatus(id, SightingStatus.DISMISSED)

  private fun setSightingStatus(
      id: String,
      status: SightingStatus,
      onUpdated: suspend () -> Unit = {},
  ) {
    if (!canManageAlert()) return
    _uiState.update { it.copy(errorMessage = null) }
    viewModelScope.launch {
      sightingRepository
          .setSightingStatus(id, status)
          .fold(
              onSuccess = { onUpdated() },
              onFailure = { failure ->
                _uiState.update {
                  it.copy(errorMessage = failure.localizedMessage ?: STATUS_UPDATE_FAILED)
                }
              },
          )
    }
  }

  /** Moves the alert's last seen place to the sighting [sightingId], if it is newer. */
  private suspend fun moveLastSeenTo(sightingId: String) {
    val sighting = _uiState.value.sightings.firstOrNull { it.id == sightingId } ?: return
    alertRepository
        .updateLastSeen(alertId, sighting.location, sighting.seenAtMillis)
        .fold(
            onSuccess = { moved ->
              if (moved) {
                _uiState.update { state ->
                  state.copy(
                      alert =
                          state.alert?.copy(
                              lastKnownLocation = sighting.location,
                              lastSeenAtMillis = sighting.seenAtMillis,
                          )
                  )
                }
              }
            },
            onFailure = { failure ->
              _uiState.update {
                it.copy(errorMessage = failure.localizedMessage ?: LAST_SEEN_UPDATE_FAILED)
              }
            },
        )
  }

  /** Closes the alert after the owner has found their pet. */
  fun markFound() {
    if (!canManageAlert()) return
    _uiState.update { it.copy(errorMessage = null) }
    viewModelScope.launch {
      alertRepository
          .closeAlert(alertId)
          .fold(
              onSuccess = {
                _uiState.update { state ->
                  state.copy(
                      alert =
                          state.alert?.copy(
                              status = AlertStatus.CLOSED,
                              closedAtMillis = now(),
                          ),
                      isClosed = true,
                  )
                }
              },
              onFailure = { failure ->
                _uiState.update { it.copy(errorMessage = failure.localizedMessage ?: CLOSE_FAILED) }
              },
          )
    }
  }

  private fun canManageAlert(): Boolean =
      _uiState.value.alert != null &&
          !_uiState.value.isClosed &&
          authRepository.currentUser.value?.uid == _uiState.value.alert?.ownerId

  companion object {
    const val NOT_OWNER = "You can only view your own alerts."
    const val ALERT_NOT_FOUND = "Alert not found."
    const val LOAD_FAILED = "Couldn't load the alert. Please try again."
    const val SIGHTINGS_LOAD_FAILED = "Couldn't load sightings. Please try again."
    const val STATUS_UPDATE_FAILED = "Couldn't update the sighting. Please try again."
    const val LAST_SEEN_UPDATE_FAILED = "Couldn't update the last seen place. Please try again."
    const val CLOSE_FAILED = "Couldn't mark the alert as found. Please try again."

    val Factory: (String) -> ViewModelProvider.Factory = { alertId ->
      viewModelFactory {
        initializer {
          MyAlertViewModel(
              alertId = alertId,
              alertRepository = AlertRepositoryFirestore(),
              sightingRepository = SightingRepositoryFirestore(),
              authRepository = AuthRepositoryProvider.authRepository,
          )
        }
      }
    }
  }
}
