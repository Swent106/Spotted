package com.android.spotted.e2e

import android.Manifest
import android.content.Context
import android.os.CancellationSignal
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreateCredentialRequest
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PrepareGetCredentialResponse
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.spotted.MainActivity
import com.android.spotted.data.location.LocationRepositoryProvider
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.location.LocationRepository
import com.android.spotted.ui.addpets.AddPetsScreenTestTags
import com.android.spotted.ui.authentification.CredentialManagerProvider
import com.android.spotted.ui.authentification.SignInScreenTestTags
import com.android.spotted.ui.components.MapPickerTestTags
import com.android.spotted.ui.home.HomeScreenTestTags
import com.android.spotted.ui.personalinfo.PersonalInfoScreenTestTags
import com.android.spotted.ui.profile.HelperProfileTestTags
import com.android.spotted.ui.report.ReportMissingScreenTestTags
import com.android.spotted.ui.sighting.ReportSightingScreenTestTags
import com.android.spotted.utils.FakeJwtGenerator
import com.android.spotted.utils.FirebaseEmulators
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end test of the main user journey, against the Firebase Auth and Firestore emulators
 * (`firebase emulators:start --only auth,firestore`): sign in, create the profile, add pets, see
 * them on Home, report one missing, report a sighting, then sign out.
 *
 * Everything the user does goes through the UI. Two test seams replace what a test can't drive: the
 * system Google account chooser ([CredentialManagerProvider], returning a token for a fixed
 * emulator account) and the GPS ([LocationRepositoryProvider], returning a fixed position).
 *
 * The number of pets comes from the `e2ePetCount` instrumentation argument (3 by default), e.g.
 * `-Pandroid.testInstrumentationRunnerArguments.e2ePetCount=5`.
 */
@RunWith(AndroidJUnit4::class)
class MainUserFlowTest {

  @get:Rule val composeTestRule = createEmptyComposeRule()

  private lateinit var scenario: ActivityScenario<MainActivity>

  /** Makes this run's pet names unique, on top of the emulators being cleared. */
  private val runId = System.currentTimeMillis().toString(36).takeLast(5)

  private val petCount =
      InstrumentationRegistry.getArguments().getString(PET_COUNT_ARGUMENT)?.toInt()
          ?: DEFAULT_PET_COUNT

  private val pets = (1..petCount).map { E2ePet(name = "Pet$it $runId", species = speciesFor(it)) }

  @Before
  fun setUp() {
    assertTrue("$PET_COUNT_ARGUMENT must be at least 1 to report a pet missing", petCount >= 1)
    FirebaseEmulators.configure()
    clearEmulators()
    FirebaseAuth.getInstance().signOut()
    CredentialManagerProvider.create = { TestCredentialManager }
    LocationRepositoryProvider.create = { FixedLocationRepository }
    grantLocationPermission()
    scenario = ActivityScenario.launch(MainActivity::class.java)
  }

  @After
  fun tearDown() {
    if (::scenario.isInitialized) scenario.close()
    FirebaseAuth.getInstance().signOut()
    CredentialManagerProvider.create = { CredentialManager.create(it) }
    LocationRepositoryProvider.create = {
      com.android.spotted.data.location.LocationRepositoryFused(it)
    }
  }

  @Test
  fun signIn_profile_pets_reportMissing_sighting_signOut() {
    signIn()
    completeProfile()
    addPets()
    assertPetsOnHome()
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    checkNotNull(uid) { "A user should be signed in after the onboarding" }
    reportFirstPetMissing(uid)
    reportSighting(uid)
    signOut()
  }

  // ---------------------------------------- Steps ----------------------------------------

  private fun signIn() {
    waitFor(SignInScreenTestTags.SCREEN, "the Sign in screen should show at start")
    node(SignInScreenTestTags.GOOGLE_BUTTON).performClick()
    // A new account has no profile: the app asks for one
    waitFor(PersonalInfoScreenTestTags.SCREEN, "Personal info should open after signing in")
    assertEquals(
        "The signed-in user should be the test account",
        TEST_EMAIL,
        FirebaseAuth.getInstance().currentUser?.email,
    )
  }

  private fun completeProfile() {
    node(PersonalInfoScreenTestTags.FIRST_NAME_INPUT).performScrollTo().performTextInput("E2E")
    node(PersonalInfoScreenTestTags.LAST_NAME_INPUT).performScrollTo().performTextInput("Tester")
    node(PersonalInfoScreenTestTags.HOME_AREA_INPUT).performScrollTo().performTextInput("Lausanne")
    node(PersonalInfoScreenTestTags.CREATE_PROFILE_BUTTON).assertIsEnabled().performClick()
    waitFor(AddPetsScreenTestTags.SCREEN, "Add pets should open once the profile is saved")
  }

  private fun addPets() {
    pets.forEachIndexed { index, pet ->
      node(AddPetsScreenTestTags.NAME_INPUT).performScrollTo().performTextInput(pet.name)
      node(AddPetsScreenTestTags.speciesChip(pet.species)).performScrollTo().performClick()
      val isLast = index == pets.lastIndex
      if (isLast) {
        node(AddPetsScreenTestTags.SAVE_AND_CONTINUE_BUTTON).assertIsEnabled().performClick()
      } else {
        node(AddPetsScreenTestTags.ADD_ANOTHER_BUTTON).performClick()
        waitUntil("\"${pet.name}\" should be listed as added") {
          composeTestRule
              .onAllNodes(hasTestTag(AddPetsScreenTestTags.ADDED_PETS) and hasText(pet.name, true))
              .fetchSemanticsNodes()
              .isNotEmpty()
        }
      }
    }
    waitFor(HomeScreenTestTags.SCREEN, "Home should open after the last pet is saved")
  }

  private fun assertPetsOnHome() {
    pets.forEach { pet ->
      waitUntil("\"${pet.name}\" should be in \"Your pets\" on Home") {
        runCatching {
              node(HomeScreenTestTags.BODY).performScrollToNode(hasText(pet.name))
              true
            }
            .getOrDefault(false)
      }
    }
  }

  private fun reportFirstPetMissing(uid: String) {
    val missing = pets.first()
    node(HomeScreenTestTags.BODY)
        .performScrollToNode(hasTestTag(HomeScreenTestTags.REPORT_MISSING_BUTTON))
    node(HomeScreenTestTags.REPORT_MISSING_BUTTON).performClick()
    waitFor(ReportMissingScreenTestTags.SCREEN, "Report missing should open")
    waitUntil("\"${missing.name}\" should be offered as the missing pet") {
      composeTestRule.onAllNodes(hasText(missing.name)).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNode(hasText(missing.name)).performScrollTo().performClick()
    // Where the pet was last seen: a tap in the middle of the map places the pin
    node(MapPickerTestTags.MAP).performScrollTo().performTouchInput { click(center) }
    waitFor(MapPickerTestTags.RADIUS_LABEL, "Tapping the map should place the last seen pin")
    node(ReportMissingScreenTestTags.PUBLISH_BUTTON)
        .performScrollTo()
        .assertIsEnabled()
        .performClick()
    waitFor(HomeScreenTestTags.SCREEN, "Home should show again once the alert is published")

    val alerts = documents("alerts", "ownerId", uid)
    assertEquals("Exactly one alert should be published", 1, alerts.size)
    assertEquals("The alert should be for the chosen pet", missing.name, alerts.single()["petName"])
    assertEquals("The alert should be open", "OPEN", alerts.single()["status"])
  }

  private fun reportSighting(uid: String) {
    node(HomeScreenTestTags.TAB_CAMERA).performClick()
    waitFor(ReportSightingScreenTestTags.SCREEN, "The Camera tab should open Report sighting")
    node(ReportSightingScreenTestTags.LOCATION_CARD).performScrollTo().performClick()
    waitUntil("The GPS position should fill the sighting's location") {
      composeTestRule.onAllNodes(hasText("Taken from your GPS")).fetchSemanticsNodes().isNotEmpty()
    }
    node(ReportSightingScreenTestTags.NO_MATCH_OPTION).performScrollTo().performClick()
    node(ReportSightingScreenTestTags.SUBMIT_BUTTON)
        .performScrollTo()
        .assertIsEnabled()
        .performClick()
    waitFor(HomeScreenTestTags.SCREEN, "Home should show again once the sighting is sent")

    val sightings = documents("sightings", "reporterId", uid)
    assertEquals("Exactly one sighting should be reported", 1, sightings.size)
    val location = sightings.single()["location"] as Map<*, *>
    assertEquals("The sighting should be at the GPS position", GPS.latitude, location["latitude"])
    assertEquals("The sighting should be at the GPS position", GPS.longitude, location["longitude"])
  }

  private fun signOut() {
    node(HomeScreenTestTags.TAB_PROFILE).performClick()
    waitFor(HelperProfileTestTags.SCREEN, "The Profile tab should open the Helper profile")
    node(HelperProfileTestTags.ACCOUNT_BUTTON).performClick()
    waitFor(HelperProfileTestTags.SIGN_OUT, "The account button should offer to sign out")
    node(HelperProfileTestTags.SIGN_OUT).performClick()
    waitFor(SignInScreenTestTags.SCREEN, "The Sign in screen should show after signing out")
    waitUntil("Firebase should have no signed-in user after signing out") {
      FirebaseAuth.getInstance().currentUser == null
    }
  }

  // ---------------------------------------- Helpers ----------------------------------------

  private fun node(tag: String): SemanticsNodeInteraction =
      composeTestRule.onNodeWithTag(tag, useUnmergedTree = false)

  private fun waitFor(tag: String, message: String) {
    waitUntil(message) {
      composeTestRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }
    node(tag).assertIsDisplayed()
  }

  private fun waitUntil(message: String, condition: () -> Boolean) {
    try {
      composeTestRule.waitUntil(TIMEOUT_MILLIS, condition)
    } catch (e: Throwable) {
      throw AssertionError("Timed out after $TIMEOUT_MILLIS ms: $message", e)
    }
  }

  /** Documents of [collection] whose [field] is [value], read from the Firestore emulator. */
  private fun documents(collection: String, field: String, value: String): List<Map<String, Any>> =
      Tasks.await(
              FirebaseFirestore.getInstance()
                  .collection(collection)
                  .whereEqualTo(field, value)
                  .limit(100)
                  .get()
          )
          .documents
          .mapNotNull { it.data }

  private fun grantLocationPermission() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val packageName = instrumentation.targetContext.packageName
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .forEach { instrumentation.uiAutomation.grantRuntimePermission(packageName, it) }
  }

  /** Deletes every account and document in the emulators, so each run starts from nothing. */
  private fun clearEmulators() {
    val projectId = FirebaseApp.getInstance().options.projectId
    delete("http://$EMULATOR_HOST:9099/emulator/v1/projects/$projectId/accounts")
    delete(
        "http://$EMULATOR_HOST:8080/emulator/v1/projects/$projectId/databases/(default)/documents"
    )
  }

  private fun delete(url: String) {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
      connection.requestMethod = "DELETE"
      val code = connection.responseCode
      assertTrue(
          "Could not clear the emulator ($url answered $code). Are the Firebase emulators running?",
          code in 200..299,
      )
    } finally {
      connection.disconnect()
    }
  }

  private data class E2ePet(val name: String, val species: Species)

  /** Returns a Google ID token for the test account, as the account chooser would. */
  private object TestCredentialManager : CredentialManager {
    private val token
      get() =
          FakeJwtGenerator.createFakeGoogleIdToken(
              name = "E2E Tester",
              email = TEST_EMAIL,
              sub = TEST_SUB,
          )

    override suspend fun getCredential(
        context: Context,
        request: GetCredentialRequest,
    ): GetCredentialResponse =
        GetCredentialResponse(
            GoogleIdTokenCredential.Builder().setId(TEST_EMAIL).setIdToken(token).build()
        )

    override fun getCredentialAsync(
        context: Context,
        request: GetCredentialRequest,
        cancellationSignal: CancellationSignal?,
        executor: Executor,
        callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>,
    ) {
      executor.execute {
        callback.onResult(
            GetCredentialResponse(
                GoogleIdTokenCredential.Builder().setId(TEST_EMAIL).setIdToken(token).build()
            )
        )
      }
    }

    override fun getCredentialAsync(
        context: Context,
        pendingGetCredentialHandle: PrepareGetCredentialResponse.PendingGetCredentialHandle,
        cancellationSignal: CancellationSignal?,
        executor: Executor,
        callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>,
    ) = throw UnsupportedOperationException("Not used by the app")

    override fun prepareGetCredentialAsync(
        request: GetCredentialRequest,
        cancellationSignal: CancellationSignal?,
        executor: Executor,
        callback: CredentialManagerCallback<PrepareGetCredentialResponse, GetCredentialException>,
    ) = throw UnsupportedOperationException("Not used by the app")

    override fun createCredentialAsync(
        context: Context,
        request: CreateCredentialRequest,
        cancellationSignal: CancellationSignal?,
        executor: Executor,
        callback: CredentialManagerCallback<CreateCredentialResponse, CreateCredentialException>,
    ) = throw UnsupportedOperationException("Not used by the app")

    override fun clearCredentialStateAsync(
        request: ClearCredentialStateRequest,
        cancellationSignal: CancellationSignal?,
        executor: Executor,
        callback: CredentialManagerCallback<Void?, ClearCredentialException>,
    ) {
      executor.execute { callback.onResult(null) }
    }

    override fun createSettingsPendingIntent() =
        throw UnsupportedOperationException("Not used by the app")
  }

  /** A fixed position, so the sighting doesn't depend on the device's GPS. */
  private object FixedLocationRepository : LocationRepository {
    override suspend fun getCurrentLocation() = Result.success(GPS)
  }

  private companion object {
    const val PET_COUNT_ARGUMENT = "e2ePetCount"
    const val DEFAULT_PET_COUNT = 3
    const val TIMEOUT_MILLIS = 20_000L
    const val EMULATOR_HOST = "10.0.2.2"
    const val TEST_EMAIL = "e2e@spotted.test"
    /** Fixed Google account id, so every run signs in as the same emulator account. */
    const val TEST_SUB = "e2e-test-account"
    val GPS = Location(latitude = 46.5191, longitude = 6.5668)

    fun speciesFor(index: Int) = Species.entries[(index - 1) % Species.entries.size]
  }
}
