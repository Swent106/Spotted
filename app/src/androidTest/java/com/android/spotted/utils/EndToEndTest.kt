package com.android.spotted.utils

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.credentials.CredentialManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.android.spotted.MainActivity
import com.android.spotted.data.location.LocationRepositoryFused
import com.android.spotted.data.location.LocationRepositoryProvider
import com.android.spotted.model.Location
import com.android.spotted.model.location.LocationRepository
import com.android.spotted.ui.authentification.CredentialManagerProvider
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.net.HttpURLConnection
import java.net.URL
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule

/**
 * Base of the end-to-end tests, which drive the whole app against the Firebase Auth and Firestore
 * emulators (`firebase emulators:start --only auth,firestore`).
 *
 * Before each test it deletes every account and document in the emulators, then launches the app
 * signed out. Two test seams replace what a test can't drive: the system Google account chooser
 * ([credentials], signing in as its [TestCredentialManager.account]) and the GPS (always [GPS]).
 */
abstract class EndToEndTest {

  @get:Rule val composeTestRule = createEmptyComposeRule()

  /** Signs in as [TestAccount.DEFAULT]; set its account to sign in as someone else. */
  protected val credentials = TestCredentialManager(TestAccount.DEFAULT)

  private lateinit var scenario: ActivityScenario<MainActivity>

  @Before
  fun launchAppOnEmptyEmulators() {
    clearEmulators()
    FirebaseAuth.getInstance().signOut()
    CredentialManagerProvider.create = { credentials }
    LocationRepositoryProvider.create = { FixedLocationRepository }
    grantLocationPermission()
    grantNotificationPermission()
    scenario = ActivityScenario.launch(MainActivity::class.java)
  }

  @After
  fun closeAppAndRestoreSeams() {
    if (::scenario.isInitialized) scenario.close()
    FirebaseAuth.getInstance().signOut()
    CredentialManagerProvider.create = { CredentialManager.create(it) }
    LocationRepositoryProvider.create = { LocationRepositoryFused(it) }
  }

  protected fun node(tag: String): SemanticsNodeInteraction =
      composeTestRule.onNodeWithTag(tag, useUnmergedTree = false)

  /**
   * Scrolls to the node when it is in a scrolling container, then clicks it. A click on a node
   * outside the screen is lost, and CI emulators have smaller screens than most phones.
   */
  protected fun SemanticsNodeInteraction.scrollToAndClick(): SemanticsNodeInteraction {
    // performScrollTo fails when no parent scrolls: the node is then always on screen
    runCatching { performScrollTo() }
    return performClick()
  }

  /** Waits until a node tagged [tag] exists, then checks that it is displayed. */
  protected fun waitFor(tag: String, message: String) {
    waitUntil(message) { exists(tag) }
    node(tag).assertIsDisplayed()
  }

  protected fun waitUntil(message: String, condition: () -> Boolean) {
    try {
      composeTestRule.waitUntil(TIMEOUT_MILLIS, condition)
    } catch (e: Throwable) {
      throw AssertionError("Timed out after $TIMEOUT_MILLIS ms: $message", e)
    }
  }

  protected fun exists(tag: String): Boolean =
      composeTestRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

  /**
   * Documents of [collection] whose [field] is [value], read from the Firestore emulator. Each map
   * also holds the document's id under [DOCUMENT_ID].
   */
  protected fun documents(
      collection: String,
      field: String,
      value: String,
  ): List<Map<String, Any>> =
      Tasks.await(
              FirebaseFirestore.getInstance()
                  .collection(collection)
                  .whereEqualTo(field, value)
                  .limit(100)
                  .get()
          )
          .documents
          .mapNotNull { document -> document.data?.plus(DOCUMENT_ID to document.id) }

  private fun grantLocationPermission() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val packageName = instrumentation.targetContext.packageName
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .forEach { instrumentation.uiAutomation.grantRuntimePermission(packageName, it) }
  }

  /** Otherwise the system dialog would cover the app right after sign-in. */
  private fun grantNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.uiAutomation.grantRuntimePermission(
        instrumentation.targetContext.packageName,
        Manifest.permission.POST_NOTIFICATIONS,
    )
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

  /** A fixed position, so the tests don't depend on the device's GPS. */
  private object FixedLocationRepository : LocationRepository {
    override suspend fun getCurrentLocation() = Result.success(GPS)
  }

  companion object {
    /** Where the fake GPS puts every user. */
    val GPS = Location(latitude = 46.5191, longitude = 6.5668)

    /** Key of the document id in the maps [documents] returns. */
    const val DOCUMENT_ID = "documentId"

    private const val TIMEOUT_MILLIS = 20_000L
    private const val EMULATOR_HOST = "10.0.2.2"
  }
}
