// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.alertdetail

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertRepository
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.User
import com.android.spotted.model.distanceKm
import com.android.spotted.model.formatDistance
import com.android.spotted.model.location.LocationException
import com.android.spotted.model.location.LocationRepository
import com.android.spotted.model.user.UserProfile
import com.android.spotted.model.user.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** [AlertDetailScreen] wired to its ViewModel: loading the alert, its distance, Back and Share. */
@RunWith(AndroidJUnit4::class)
class AlertDetailScreenWiringTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val alert =
      Alert(
          id = "alert-1",
          petId = "pet-1",
          ownerId = "owner-1",
          lastKnownLocation = Location(46.5197, 6.6323),
          lostAtMillis = 1_700_000_000_000L,
          petName = "Luna",
          petSpecies = Species.CAT,
      )

  /** Keeps the activities the screen starts instead of starting them. */
  private class RecordingContext(base: Context) : ContextWrapper(base) {
    val started = mutableListOf<Intent>()

    override fun startActivity(intent: Intent) {
      started += intent
    }
  }

  private val context = RecordingContext(InstrumentationRegistry.getInstrumentation().targetContext)

  /** Granted beforehand, so the system permission dialog never covers the screen. */
  @Before
  fun grantLocationPermission() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val packageName = instrumentation.targetContext.packageName
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .forEach { instrumentation.uiAutomation.grantRuntimePermission(packageName, it) }
  }

  private fun setScreen(
      onBack: () -> Unit = {},
      viewModel: AlertDetailViewModel = AlertDetailViewModel(SingleAlertRepository(alert)),
  ) {
    composeTestRule.setContent {
      // The recording context is not an activity: keep the activity's result registry, which the
      // location permission request needs
      val registryOwner = checkNotNull(LocalActivityResultRegistryOwner.current)
      CompositionLocalProvider(
          LocalContext provides context,
          LocalActivityResultRegistryOwner provides registryOwner,
      ) {
        AlertDetailScreen(
            viewModel = viewModel,
            alertId = alert.id,
            onReportSighting = {},
            onBack = onBack,
        )
      }
    }
    composeTestRule.waitUntil {
      composeTestRule
          .onAllNodes(hasTestTag(AlertDetailTestTags.NAME))
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
  }

  @Test
  fun backButton_callsOnBack() {
    var backs = 0
    setScreen(onBack = { backs++ })

    composeTestRule.onNodeWithTag(AlertDetailTestTags.BACK).performClick()

    assertEquals(1, backs)
  }

  @Test
  fun shareButton_opensTheShareSheetWithThePetAndPlace() {
    setScreen()

    composeTestRule.onNodeWithTag(AlertDetailTestTags.SHARE).performClick()
    composeTestRule.waitForIdle()

    val chooser = context.started.single()
    assertEquals(Intent.ACTION_CHOOSER, chooser.action)
    @Suppress("DEPRECATION") val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
    assertEquals(Intent.ACTION_SEND, send.action)
    assertEquals("text/plain", send.type)
    val text = send.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
    assertTrue(text, text.startsWith("Luna is missing near 46.5197, 6.6323."))
  }

  @Test
  fun withTheLocationPermission_distanceIsMeasuredFromTheGps() {
    setScreen(
        viewModel =
            AlertDetailViewModel(
                SingleAlertRepository(alert),
                locationRepository = FixedLocationRepository(Result.success(GPS)),
            )
    )

    composeTestRule.waitUntil { distanceShown() }
    composeTestRule
        .onNodeWithTag(AlertDetailTestTags.DISTANCE)
        .assertTextEquals(formatDistance(GPS.distanceKm(alert.lastKnownLocation)))
  }

  @Test
  fun withoutTheGpsPosition_distanceIsMeasuredFromTheHome() {
    setScreen(
        viewModel =
            AlertDetailViewModel(
                SingleAlertRepository(alert),
                locationRepository =
                    FixedLocationRepository(Result.failure(LocationException.PermissionDenied())),
                authRepository = SignedInAuthRepository,
                userRepository = HomeUserRepository,
            )
    )

    composeTestRule.waitUntil { distanceShown() }
    composeTestRule
        .onNodeWithTag(AlertDetailTestTags.DISTANCE)
        .assertTextEquals(formatDistance(HOME.distanceKm(alert.lastKnownLocation)))
  }

  private fun distanceShown() =
      composeTestRule
          .onAllNodes(hasTestTag(AlertDetailTestTags.DISTANCE))
          .fetchSemanticsNodes()
          .isNotEmpty()

  /** Gives [result] as the device position (the real GPS is not used in tests). */
  private class FixedLocationRepository(private val result: Result<Location>) : LocationRepository {
    override suspend fun getCurrentLocation() = result
  }

  /** A user signed in as [USER_ID]. */
  private object SignedInAuthRepository : AuthRepository {
    override val currentUser = MutableStateFlow<User?>(User(USER_ID, "me@spotted.test"))

    override suspend fun signInWithGoogle(idToken: String) = Result.success(currentUser.value!!)

    override suspend fun signOut() = Result.success(Unit)

    override suspend fun deleteAccount() = Result.success(Unit)
  }

  /** The profile of [USER_ID], whose home pin is [HOME]. */
  private object HomeUserRepository : UserRepository {
    override suspend fun getUser(uid: String) =
        Result.success(
            UserProfile(uid = uid, name = "Me", email = "me@spotted.test", homeLocation = HOME)
        )

    override suspend fun saveUser(user: UserProfile) = Result.success(Unit)

    override suspend fun deleteUser(uid: String) = Result.success(Unit)
  }

  private companion object {
    const val USER_ID = "me"
    // About 600 m from the alert, and about 50 km for the home: the two distances differ
    val GPS = Location(46.5197, 6.6400)
    val HOME = Location(46.2044, 6.1432)
  }

  /**
   * Returns [alert] for its id; the unit-test fake lives in `test/`, which `androidTest` can't use.
   */
  private class SingleAlertRepository(private val alert: Alert) : AlertRepository {
    override fun getNewId() = "alert-2"

    override suspend fun publishAlert(alert: Alert): Result<Unit> = Result.success(Unit)

    override suspend fun getAlertById(id: String): Result<Alert?> =
        Result.success(alert.takeIf { it.id == id })

    override suspend fun getMyAlerts(ownerId: String) =
        Result.success(listOf(alert).filter { it.ownerId == ownerId })

    // Not used in this test
    override suspend fun closeAlert(alertId: String): Result<Unit> = Result.success(Unit)

    override suspend fun getOpenAlertsNear(center: Location, radiusKm: Double) =
        Result.success(listOf(alert))

    override suspend fun getCachedOpenAlertsNear(
        center: Location,
        radiusKm: Double,
        limit: Int,
    ) = Result.success(listOf(alert).take(limit))

    override suspend fun getOpenAlertsByOwner(ownerId: String) =
        Result.success(listOf(alert).filter { it.ownerId == ownerId })

    override suspend fun deleteAlertsByOwner(ownerId: String): Result<Unit> = Result.success(Unit)
  }
}
