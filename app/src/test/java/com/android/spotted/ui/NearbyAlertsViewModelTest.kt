// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.model.distanceKm
import com.android.spotted.model.fakes.FakeAlertRepository
import com.android.spotted.model.notification.Notifier
import com.android.spotted.model.notification.SightingNotification
import com.android.spotted.model.user.FakeUserRepository
import com.android.spotted.model.user.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NearbyAlertsViewModelTest {

  private val authRepository = FakeAuthRepository()
  private val userRepository = FakeUserRepository()
  private val alertRepository = FakeAlertRepository()
  private val notifier = RecordingNotifier()
  private lateinit var uid: String

  @Before
  fun setUp() = runTest {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    uid = authRepository.signInWithGoogle("token").getOrThrow().uid
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun newAlertNearHome_isNotified_withItsDistance() = runTest {
    saveProfile(home = HOME)
    startWatching()

    publish(alert("a1", NEAR))

    assertEquals(listOf("a1"), notifier.alertIds)
    assertEquals(HOME.distanceKm(NEAR), notifier.distances.single(), 1e-9)
  }

  @Test
  fun alertsOpenAtStart_areNotNotified() = runTest {
    saveProfile(home = HOME)
    publish(alert("a1", NEAR))

    startWatching()
    publish(alert("a2", NEAR))

    assertEquals(listOf("a2"), notifier.alertIds)
  }

  @Test
  fun ownAlert_andFarAlert_areNotNotified() = runTest {
    saveProfile(home = HOME)
    startWatching()

    publish(alert("mine", NEAR, ownerId = uid))
    publish(alert("far", FAR))

    assertEquals(emptyList<String>(), notifier.alertIds)
  }

  @Test
  fun withoutHomePin_nothingIsNotified() = runTest {
    saveProfile(home = null)
    startWatching()

    publish(alert("a1", NEAR))

    assertEquals(emptyList<String>(), notifier.alertIds)
  }

  @Test
  fun signedOut_nothingIsNotified() = runTest {
    saveProfile(home = HOME)
    authRepository.signOut()
    startWatching()

    publish(alert("a1", NEAR))

    assertEquals(emptyList<String>(), notifier.alertIds)
  }

  @Test
  fun radiusChange_restartsWatching_withTheNewRadius() = runTest {
    saveProfile(home = HOME, radiusKm = 1)
    startWatching()
    publish(alert("far", FAR))

    saveProfile(home = HOME, radiusKm = 5)
    publish(alert("far2", FAR))

    // "far" was already open when the new radius started: only "far2" is news
    assertEquals(listOf("far2"), notifier.alertIds)
  }

  private fun startWatching() =
      NearbyAlertsViewModel(authRepository, userRepository, alertRepository, notifier)

  private suspend fun saveProfile(home: Location?, radiusKm: Int = 3) {
    userRepository.saveUser(
        UserProfile(
            uid = uid,
            name = "Me",
            email = "me@spotted.test",
            homeLocation = home,
            alertRadiusKm = radiusKm,
        )
    )
  }

  private suspend fun publish(alert: Alert) {
    alertRepository.publishAlert(alert).getOrThrow()
  }

  private fun alert(id: String, location: Location, ownerId: String = "someone") =
      Alert(
          id = id,
          petId = "pet-$id",
          ownerId = ownerId,
          lastKnownLocation = location,
          lostAtMillis = 0L,
          petName = "Milo",
          petSpecies = Species.DOG,
      )

  private class RecordingNotifier : Notifier {
    val alertIds = mutableListOf<String>()
    val distances = mutableListOf<Double>()

    override fun notifySighting(notification: SightingNotification) = Unit

    override fun notifyNearbyAlert(alert: Alert, distanceKm: Double) {
      alertIds += alert.id
      distances += distanceKm
    }
  }

  private companion object {
    val HOME = Location(46.5197, 6.6323)
    // About 560 m and 3.3 km north of HOME
    val NEAR = Location(46.5247, 6.6323)
    val FAR = Location(46.5497, 6.6323)
  }
}
