package com.android.spotted.ui.sighting

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
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
    composeTestRule.onNodeWithTag(ReportSightingScreenTestTags.SUBMIT_BUTTON).assertIsDisplayed()
  }

  @Test
  fun submitButton_disabledWhenNoOptionSelected() {
    val state = ReportSightingUiState(location = Location(46.5, 6.6))

    composeTestRule.setContent { ReportSightingContent(state = state) }

    composeTestRule.onNodeWithTag(ReportSightingScreenTestTags.SUBMIT_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun submitButton_enabledWhenOptionAndLocationSelected() {
    val state =
        ReportSightingUiState(
            isNoMatchingAlertSelected = true,
            location = Location(46.5, 6.6),
            detectedSpecies = Species.CAT,
        )

    composeTestRule.setContent { ReportSightingContent(state = state) }

    composeTestRule.onNodeWithTag(ReportSightingScreenTestTags.SUBMIT_BUTTON).assertIsDisplayed()
  }

  @Test
  fun loader_displayedWhenSubmitting() {
    val state = ReportSightingUiState(isSubmitting = true)

    composeTestRule.setContent { ReportSightingContent(state = state) }

    composeTestRule.onNodeWithTag(ReportSightingScreenTestTags.LOADER).assertIsDisplayed()
  }

  @Test
  fun errorMessage_displayedWhenPresent() {
    val state = ReportSightingUiState(errorMessage = "Submission failed")

    composeTestRule.setContent { ReportSightingContent(state = state) }

    composeTestRule
        .onNodeWithTag(ReportSightingScreenTestTags.ERROR_MESSAGE)
        .assertIsDisplayed()
        .assertTextEquals("Submission failed")
  }
}
