package com.android.spotted.ui.sighting

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
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
        .assertIsDisplayed()
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
  fun reportSightingScreen_wrapper_rendersContent_and_callsOnSubmitted() {
    var submittedCalled = false

    composeTestRule.setContent {
      ReportSightingScreen(
          onSubmitted = { submittedCalled = true },
      )
    }

    composeTestRule.onNodeWithTag(ReportSightingScreenTestTags.SCREEN).assertIsDisplayed()
    assertTrue(!submittedCalled)
  }
}
