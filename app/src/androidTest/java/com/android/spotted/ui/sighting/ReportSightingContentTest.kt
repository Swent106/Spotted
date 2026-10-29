package com.android.spotted.ui.sighting

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.model.location.LocationRepository
import com.android.spotted.model.photo.FakePhotoRepository
import com.android.spotted.model.sighting.Sighting
import com.android.spotted.model.sighting.SightingRepository
import com.android.spotted.model.sighting.SightingStatus
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportSightingContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun displaysAllComponents_correctly() {
    val state =
        ReportSightingUiState(
            nearbyAlerts =
                listOf(
                    NearbyAlertOption(
                        alertId = "alert_1",
                        petName = "Milo",
                        description = "Orange tabby",
                    ),
                ),
        )

    composeTestRule.setContent { ReportSightingContent(state = state) }

    composeTestRule.onNodeWithTag(ReportSightingScreenTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ReportSightingScreenTestTags.PHOTO_AREA).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ReportSightingScreenTestTags.PHOTO_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ReportSightingScreenTestTags.LOCATION_CARD).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag("${ReportSightingScreenTestTags.PET_OPTION_PREFIX}alert_1")
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(ReportSightingScreenTestTags.NO_MATCH_OPTION).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(ReportSightingScreenTestTags.SUBMIT_BUTTON)
        .performScrollTo()
        .assertIsDisplayed()
  }

  @Test
  fun submitButton_disabledWhenNoOptionSelected() {
    val state = ReportSightingUiState(location = Location(46.5, 6.6))

    composeTestRule.setContent { ReportSightingContent(state = state) }

    composeTestRule
        .onNodeWithTag(ReportSightingScreenTestTags.SUBMIT_BUTTON)
        .performScrollTo()
        .assertIsNotEnabled()
  }

  @Test
  fun submitButton_enabledWhenOptionAndLocationSelected() {
    val state =
        ReportSightingUiState(
            selectedSpecies = Species.CAT,
            isNoMatchingAlertSelected = true,
            location = Location(46.5, 6.6),
            detectedSpecies = Species.CAT,
        )

    composeTestRule.setContent { ReportSightingContent(state = state) }

    composeTestRule
        .onNodeWithTag(ReportSightingScreenTestTags.SUBMIT_BUTTON)
        .performScrollTo()
        .assertIsEnabled()
  }

  @Test
  fun loader_displayedWhenSubmitting() {
    val state = ReportSightingUiState(isSubmitting = true)

    composeTestRule.setContent { ReportSightingContent(state = state) }

    composeTestRule
        .onNodeWithTag(ReportSightingScreenTestTags.LOADER)
        .performScrollTo()
        .assertIsDisplayed()
  }

  @Test
  fun errorMessage_displayedWhenPresent() {
    val state = ReportSightingUiState(errorMessage = "Submission failed")

    composeTestRule.setContent { ReportSightingContent(state = state) }

    composeTestRule
        .onNodeWithTag(ReportSightingScreenTestTags.ERROR_MESSAGE)
        .performScrollTo()
        .assertIsDisplayed()
        .assertTextEquals("Submission failed")
  }

  @Test
  fun photoAndSpeciesTag_displayedWhenPresent() {
    val state =
        ReportSightingUiState(
            photoUri = "file:///fake/photo.jpg",
            detectedSpecies = Species.CAT,
        )

    composeTestRule.setContent { ReportSightingContent(state = state) }

    composeTestRule.onNodeWithText("Looks like a cat · you can correct this").assertIsDisplayed()
    composeTestRule.onNodeWithText("Retake").assertIsDisplayed()
  }

  @Test
  fun offlineBanner_displayedWhenOffline() {
    val state = ReportSightingUiState(isOffline = true)

    composeTestRule.setContent { ReportSightingContent(state = state) }

    composeTestRule.onNodeWithText("You are offline").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun nearbyAlert_withPhotoUrl_and_selectedState_rendersCorrectly() {
    val state =
        ReportSightingUiState(
            selectedAlertId = "alert_1",
            isNoMatchingAlertSelected = false,
            nearbyAlerts =
                listOf(
                    NearbyAlertOption(
                        alertId = "alert_1",
                        petName = "Milo",
                        description = "Orange cat",
                        photoUrl = "https://example.com/photo.jpg",
                    ),
                ),
        )

    composeTestRule.setContent { ReportSightingContent(state = state) }

    composeTestRule
        .onNodeWithTag("${ReportSightingScreenTestTags.PET_OPTION_PREFIX}alert_1")
        .assertIsDisplayed()
  }

  @Test
  fun clicks_triggerCallbacks() {
    var locationClicked = false
    var alertSelected: String? = null
    var noMatchClicked = false
    var submitClicked = false

    val state =
        ReportSightingUiState(
            selectedSpecies = Species.DOG,
            location = Location(46.5, 6.6),
            isNoMatchingAlertSelected = true,
            nearbyAlerts =
                listOf(
                    NearbyAlertOption(
                        alertId = "alert_1",
                        petName = "Milo",
                        description = "Orange tabby",
                    ),
                ),
        )

    composeTestRule.setContent {
      ReportSightingContent(
          state = state,
          onLocationClick = { locationClicked = true },
          onAlertSelected = { id -> alertSelected = id },
          onNoMatchSelected = { noMatchClicked = true },
          onSubmitClick = { submitClicked = true },
      )
    }

    composeTestRule.onNodeWithTag(ReportSightingScreenTestTags.LOCATION_CARD).performClick()
    assertTrue(locationClicked)

    composeTestRule
        .onNodeWithTag("${ReportSightingScreenTestTags.PET_OPTION_PREFIX}alert_1")
        .performClick()
    assertEquals("alert_1", alertSelected)

    composeTestRule.onNodeWithTag(ReportSightingScreenTestTags.NO_MATCH_OPTION).performClick()
    assertTrue(noMatchClicked)

    composeTestRule
        .onNodeWithTag(ReportSightingScreenTestTags.SUBMIT_BUTTON)
        .performScrollTo()
        .performClick()
    assertTrue(submitClicked)
  }

  @Test
  fun reportSightingScreen_wrapper_fullInteraction_triggersSubmittedCallback() = runBlocking {
    var submittedCalled = false
    val sightingRepo =
        object : SightingRepository {
          override fun getNewId(): String = "id_1"

          override suspend fun reportSighting(sighting: Sighting): Result<Unit> =
              Result.success(Unit)

          override suspend fun getSightingsForAlert(alertId: String): Result<List<Sighting>> =
              Result.success(emptyList())

          override fun observeSightingsForAlert(alertId: String) =
              flowOf(Result.success(emptyList<Sighting>()))

          override suspend fun setSightingStatus(sightingId: String, status: SightingStatus) =
              Result.success(Unit)
        }
    val photoRepo = FakePhotoRepository()
    val authRepo = FakeAuthRepository()
    authRepo.signInWithGoogle("token")

    val viewModel = ReportSightingViewModel(sightingRepo, photoRepo, authRepository = authRepo)
    viewModel.setSpecies(Species.CAT)
    viewModel.setLocation(Location(46.5, 6.6))
    viewModel.selectNoMatchingAlert()

    composeTestRule.setContent {
      ReportSightingScreen(
          viewModel = viewModel,
          onSubmitted = { submittedCalled = true },
      )
    }

    composeTestRule.onNodeWithTag(ReportSightingScreenTestTags.LOCATION_CARD).performClick()
    composeTestRule
        .onNodeWithTag(ReportSightingScreenTestTags.SUBMIT_BUTTON)
        .performScrollTo()
        .performClick()

    composeTestRule.waitUntil(TIMEOUT_MILLIS) { submittedCalled }
    assertTrue(submittedCalled)
  }

  @Test
  fun reportSightingScreen_locationGranted_prefillsGpsPosition() {
    grantLocationPermission()
    val viewModel = viewModelWithFakes()

    composeTestRule.setContent { ReportSightingScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithTag(ReportSightingScreenTestTags.LOCATION_CARD).performClick()
    composeTestRule.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.location != null }

    assertEquals(GPS_LOCATION, viewModel.uiState.value.location)
    composeTestRule.onNodeWithText("Taken from your GPS").assertIsDisplayed()
  }

  @Test
  fun reportSightingScreen_successfulSubmit_callsOnSubmitted() {
    grantLocationPermission()
    val viewModel = viewModelWithFakes()
    var submittedCalled = false

    composeTestRule.setContent {
      ReportSightingScreen(viewModel = viewModel, onSubmitted = { submittedCalled = true })
    }

    composeTestRule.onNodeWithTag(ReportSightingScreenTestTags.LOCATION_CARD).performClick()
    composeTestRule.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.location != null }
    composeTestRule
        .onNodeWithTag(ReportSightingScreenTestTags.NO_MATCH_OPTION)
        .performScrollTo()
        .performClick()
    composeTestRule
        .onNodeWithTag(ReportSightingScreenTestTags.SUBMIT_BUTTON)
        .performScrollTo()
        .performClick()

    composeTestRule.waitUntil(TIMEOUT_MILLIS) { submittedCalled }
  }

  private fun grantLocationPermission() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.uiAutomation.grantRuntimePermission(
        instrumentation.targetContext.packageName,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )
  }

  /** A ViewModel with a signed-in user and fakes only, so no Firebase or real GPS is needed. */
  private fun viewModelWithFakes(): ReportSightingViewModel {
    val authRepository = FakeAuthRepository()
    runBlocking { authRepository.signInWithGoogle("token") }
    return ReportSightingViewModel(
        sightingRepository = InMemorySightingRepository(),
        photoRepository = FakePhotoRepository(),
        locationRepository =
            object : LocationRepository {
              override suspend fun getCurrentLocation() = Result.success(GPS_LOCATION)
            },
        authRepository = authRepository,
    )
  }

  /** Keeps reported sightings in memory (the unit test fake is not visible from androidTest). */
  private class InMemorySightingRepository : SightingRepository {
    private val sightings = mutableListOf<Sighting>()

    override fun getNewId() = "sighting-${sightings.size + 1}"

    override suspend fun reportSighting(sighting: Sighting): Result<Unit> {
      sightings.add(sighting)
      return Result.success(Unit)
    }

    override suspend fun getSightingsForAlert(alertId: String) =
        Result.success(sightings.filter { it.alertId == alertId })

    override fun observeSightingsForAlert(alertId: String) =
        flowOf(Result.success(sightings.filter { it.alertId == alertId }))

    override suspend fun setSightingStatus(sightingId: String, status: SightingStatus) =
        Result.success(Unit)
  }

  private companion object {
    val GPS_LOCATION = Location(46.52, 6.63)
    const val TIMEOUT_MILLIS = 5_000L
  }
}
