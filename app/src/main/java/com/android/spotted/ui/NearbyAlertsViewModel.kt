// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.spotted.data.alert.AlertRepositoryFirestore
import com.android.spotted.data.user.UserRepositoryFirestore
import com.android.spotted.model.Location
import com.android.spotted.model.alert.AlertRepository
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.AuthRepositoryProvider
import com.android.spotted.model.distanceKm
import com.android.spotted.model.notification.Notifier
import com.android.spotted.model.notification.alertsToNotify
import com.android.spotted.model.user.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/**
 * While the app runs, notifies the signed-in user of each alert published near their home pin,
 * within their alert radius. Their own alerts and the alerts already open when watching starts are
 * not notified. Without a home pin nothing is; if the pin or the radius changes, it starts again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NearbyAlertsViewModel(
    authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val alertRepository: AlertRepository,
    private val notifier: Notifier,
) : ViewModel() {

  /** Ids of the alerts notified, or open when watching started, in this session. */
  private val notified = mutableSetOf<String>()

  init {
    viewModelScope.launch {
      authRepository.currentUser
          .map { it?.uid }
          .distinctUntilChanged()
          .flatMapLatest { uid -> if (uid == null) emptyFlow() else watchHome(uid) }
          .collect {}
    }
  }

  private fun watchHome(uid: String): Flow<Unit> =
      userRepository
          .observeUser(uid)
          .mapNotNull { it.getOrNull() }
          .map { it.homeLocation to it.alertRadiusKm }
          .distinctUntilChanged()
          .flatMapLatest { (home, radiusKm) ->
            if (home == null) emptyFlow() else notifyNewAlerts(uid, home, radiusKm)
          }

  private fun notifyNewAlerts(uid: String, home: Location, radiusKm: Int): Flow<Unit> {
    var isFirstList = true
    return alertRepository
        .observeOpenAlertsNear(home, radiusKm.toDouble())
        .mapNotNull { it.getOrNull() }
        .map { alerts ->
          val newAlerts = alertsToNotify(notified, alerts, uid)
          notified += newAlerts.map { it.id }
          // The first list holds the alerts already open: they are not news
          if (isFirstList) isFirstList = false
          else
              newAlerts.forEach {
                notifier.notifyNearbyAlert(it, home.distanceKm(it.lastKnownLocation))
              }
        }
  }

  companion object {
    /** [notifier] comes from the View, which owns the `Context` it needs. */
    fun factory(notifier: Notifier): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        NearbyAlertsViewModel(
            authRepository = AuthRepositoryProvider.authRepository,
            userRepository = UserRepositoryFirestore(),
            alertRepository = AlertRepositoryFirestore(),
            notifier = notifier,
        )
      }
    }
  }
}
