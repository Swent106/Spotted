// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.spotted.data.alert.AlertRepositoryFirestore
import com.android.spotted.data.notification.NotificationRepositoryDefault
import com.android.spotted.data.sighting.SightingRepositoryFirestore
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.AuthRepositoryProvider
import com.android.spotted.model.notification.NotificationRepository
import com.android.spotted.model.notification.Notifier
import com.android.spotted.model.notification.SightingNotification
import com.android.spotted.model.notification.newIds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Posts a system notification for each sighting reported on the signed-in owner's alerts while the
 * app is running. It belongs to the activity rather than a screen: it keeps watching with the app
 * in the background, and survives a rotation, which would otherwise notify everything again.
 *
 * Sightings already pending when the owner's watch starts are not notified, only later ones; the
 * Notifications screen shows the rest. A failed read notifies nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SightingNotifierViewModel(
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository,
    private val notifier: Notifier,
) : ViewModel() {

  private val _uiState = MutableStateFlow(SightingNotifierUiState())
  val uiState: StateFlow<SightingNotifierUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      authRepository.currentUser
          .map { it?.uid }
          .distinctUntilChanged()
          .onEach { uid -> _uiState.value = SightingNotifierUiState(isSignedIn = uid != null) }
          .flatMapLatest { uid -> if (uid == null) emptyFlow() else newSightingsOf(uid) }
          .collect(notifier::notifySighting)
    }
  }

  /** Each sighting of [ownerId] that appears after the first list loaded, once. */
  private fun newSightingsOf(ownerId: String): Flow<SightingNotification> = flow {
    // Null until the first list loaded: that list is the starting point, not news
    var alreadyNotified: Set<String>? = null
    notificationRepository.observePendingSightings(ownerId).collect { result ->
      val current = result.getOrNull() ?: return@collect
      val currentIds = current.map { it.sightingId }
      alreadyNotified?.let { notified ->
        val fresh = newIds(notified, currentIds).toSet()
        current.filter { it.sightingId in fresh }.forEach { emit(it) }
      }
      alreadyNotified = alreadyNotified.orEmpty() + currentIds
    }
  }

  companion object {
    /** Builds the ViewModel around a [Notifier], which needs the app's context to exist. */
    val Factory: (Notifier) -> ViewModelProvider.Factory = { notifier ->
      viewModelFactory {
        initializer {
          SightingNotifierViewModel(
              authRepository = AuthRepositoryProvider.authRepository,
              notificationRepository =
                  NotificationRepositoryDefault(
                      alertRepository = AlertRepositoryFirestore(),
                      sightingRepository = SightingRepositoryFirestore(),
                  ),
              notifier = notifier,
          )
        }
      }
    }
  }
}
