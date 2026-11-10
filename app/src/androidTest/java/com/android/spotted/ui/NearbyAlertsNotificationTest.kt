// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.test.platform.app.InstrumentationRegistry
import com.android.spotted.data.notification.SystemNotifier
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertRepository
import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.model.distanceKm
import com.android.spotted.model.user.FakeUserRepository
import com.android.spotted.model.user.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** [NearbyAlertsViewModel] with fake repositories and the real [SystemNotifier]. */
class NearbyAlertsNotificationTest {

  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context = instrumentation.targetContext
  private val manager =
      context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  private val alertRepository = LiveAlertRepository()

  @Before
  fun setUp() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      instrumentation.uiAutomation.grantRuntimePermission(
          context.packageName,
          Manifest.permission.POST_NOTIFICATIONS,
      )
    }
    manager.cancelAll()
  }

  @After
  fun tearDown() {
    manager.cancelAll()
  }

  @Test
  fun aNewAlertNearHome_isPosted_andAFarOneIsNot() {
    val authRepository = FakeAuthRepository()
    val userRepository = FakeUserRepository()
    runBlocking {
      val uid = authRepository.signInWithGoogle("token").getOrThrow().uid
      userRepository.saveUser(
          UserProfile(uid = uid, name = "Me", email = "me@spotted.test", homeLocation = HOME)
      )
    }
    NearbyAlertsViewModel(authRepository, userRepository, alertRepository, SystemNotifier(context))
    alertRepository.awaitWatched()

    alertRepository.publish(alert("far", FAR))
    alertRepository.publish(alert("near", NEAR))

    val posted = awaitPostedNotification()
    assertEquals("near", posted.tag)
    assertEquals(SystemNotifier.CHANNEL_NEARBY_ALERTS, posted.notification.channelId)
  }

  /** notify() returns before Android has posted the notification, so wait for it to appear. */
  private fun awaitPostedNotification(): StatusBarNotification {
    val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
    while (SystemClock.uptimeMillis() < deadline) {
      val active = manager.activeNotifications
      if (active.isNotEmpty()) return active.single()
      SystemClock.sleep(POLL_INTERVAL_MILLIS)
    }
    throw AssertionError("The notification was not posted within $TIMEOUT_MILLIS ms")
  }

  private fun alert(id: String, location: Location) =
      Alert(
          id = id,
          petId = "pet-$id",
          ownerId = "someone",
          lastKnownLocation = location,
          lostAtMillis = 0L,
          petName = "Milo",
          petSpecies = Species.DOG,
      )

  /**
   * Live open alerts, filtered by distance like the real repository; the unit-test fake lives in
   * `test/`, which `androidTest` can't use. Only [observeOpenAlertsNear] is used.
   */
  private class LiveAlertRepository : AlertRepository {
    private val alerts = MutableStateFlow<List<Alert>>(emptyList())

    fun publish(alert: Alert) = alerts.update { it + alert }

    /** Waits until the ViewModel collects the alerts, so the next ones are new to it. */
    fun awaitWatched() {
      val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
      while (alerts.subscriptionCount.value == 0) {
        if (SystemClock.uptimeMillis() > deadline) throw AssertionError("Alerts never watched")
        SystemClock.sleep(POLL_INTERVAL_MILLIS)
      }
    }

    override fun observeOpenAlertsNear(
        center: Location,
        radiusKm: Double,
    ): Flow<Result<List<Alert>>> = alerts.map { all ->
      Result.success(all.filter { center.distanceKm(it.lastKnownLocation) <= radiusKm })
    }

    override fun getNewId() = error("unused")

    override suspend fun publishAlert(alert: Alert) = error("unused")

    override suspend fun getAlertById(id: String) = error("unused")

    override suspend fun getMyAlerts(ownerId: String) = error("unused")

    override suspend fun closeAlert(alertId: String) = error("unused")

    override suspend fun getOpenAlertsNear(center: Location, radiusKm: Double) = error("unused")

    override suspend fun getCachedOpenAlertsNear(center: Location, radiusKm: Double, limit: Int) =
        error("unused")

    override suspend fun getOpenAlertsByOwner(ownerId: String) = error("unused")

    override suspend fun deleteAlertsByOwner(ownerId: String) = error("unused")
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
    const val POLL_INTERVAL_MILLIS = 100L
    val HOME = Location(46.5197, 6.6323)
    // About 560 m and 3.3 km north of HOME; the default alert radius is 3 km
    val NEAR = Location(46.5247, 6.6323)
    val FAR = Location(46.5497, 6.6323)
  }
}
