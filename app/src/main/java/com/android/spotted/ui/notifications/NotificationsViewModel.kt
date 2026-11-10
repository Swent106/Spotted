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
import com.android.spotted.model.notification.SightingNotification
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel of the Notifications screen. It follows the signed-in user: each time the user changes,
 * the state is reset and the new user's pending sightings are observed, so a previous user's
 * sightings are never shown. When nobody is signed in, the list is empty.
 *
 * Tapping a notification sets [NotificationsUiState.destinationAlertId]; the screen navigates to
 * "My alert" and calls [onNavigated].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModel(
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(NotificationsUiState())
  val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

  /** Bumped by [retry]: the list of the current user is observed again from scratch. */
  private val retries = MutableStateFlow(0)

  /** Uid whose list is in [uiState]; results for any other uid are dropped. */
  private var shownUid: String? = null

  init {
    viewModelScope.launch {
      authRepository.currentUser
          .map { it?.uid }
          .distinctUntilChanged()
          .combine(retries) { uid, _ -> uid }
          // The reset travels through the stream, after anything already queued for the previous
          // user, and every state write happens in collect: a late result can't overwrite it
          .flatMapLatest { uid -> updatesFor(uid) }
          .collect { update -> apply(update) }
    }
  }

  /** Loads the list again, e.g. after a failure ("Retry"). */
  fun retry() {
    retries.update { it + 1 }
  }

  /** Opens "My alert" for the alert [notification] was reported on. */
  fun open(notification: SightingNotification) {
    _uiState.update { it.copy(destinationAlertId = notification.alertId) }
  }

  /** Called by the screen once it navigated, so going back doesn't navigate again. */
  fun onNavigated() {
    _uiState.update { it.copy(destinationAlertId = null) }
  }

  /** What happens to the state: a reset for a (new) user, or a result for one. */
  private sealed interface Update {
    data class Reset(val uid: String?) : Update

    data class Loaded(val uid: String, val result: Result<List<SightingNotification>>) : Update
  }

  private fun updatesFor(uid: String?): Flow<Update> =
      if (uid == null) flowOf(Update.Reset(null))
      else
          notificationRepository
              .observePendingSightings(uid)
              .map<Result<List<SightingNotification>>, Update> { Update.Loaded(uid, it) }
              .onStart { emit(Update.Reset(uid)) }

  private fun apply(update: Update) {
    when (update) {
      is Update.Reset -> {
        shownUid = update.uid
        _uiState.value = NotificationsUiState(isLoading = update.uid != null)
      }
      is Update.Loaded -> if (update.uid == shownUid) show(update.result)
    }
  }

  private fun show(result: Result<List<SightingNotification>>) {
    _uiState.update { state ->
      result.fold(
          onSuccess = { state.copy(items = it, isLoading = false, errorMessage = null) },
          // Keep the last list: a failing listener shouldn't wipe what the owner already sees
          onFailure = { state.copy(isLoading = false, errorMessage = LOAD_FAILED) },
      )
    }
  }

  companion object {
    const val LOAD_FAILED = "Couldn't load your notifications"

    /** Builds the ViewModel with the shared auth repository and the Firestore repositories. */
    val Factory: ViewModelProvider.Factory = viewModelFactory {
      initializer {
        NotificationsViewModel(
            authRepository = AuthRepositoryProvider.authRepository,
            notificationRepository =
                NotificationRepositoryDefault(
                    alertRepository = AlertRepositoryFirestore(),
                    sightingRepository = SightingRepositoryFirestore(),
                ),
        )
      }
    }
  }
}
