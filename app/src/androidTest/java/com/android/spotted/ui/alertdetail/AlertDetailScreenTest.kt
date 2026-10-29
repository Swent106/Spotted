// Portions of this code were generated with the help of Copilot.
// Portions of this code were generated with the help of GitHub Copilot.
package com.android.spotted.ui.alertdetail

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlertDetailScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun foundState_showsPetDetailsHighlightedNeedsAndReportAction() {
    var reportClicked = false
    val alert =
        Alert(
            id = "alert-1",
            petId = "pet-1",
            ownerId = "owner-1",
            lastKnownLocation = Location(46.5197, 6.6323),
            lostAtMillis = 1_700_000_000_000L,
            petName = "Luna",
            petSpecies = Species.CAT,
            petPhotoUrl = "https://example.com/luna.jpg",
            petAllergies = listOf("chicken"),
            petBehaviors = Behavior.entries.toSet(),
        )

    composeTestRule.setContent {
      AlertDetailContent(
          state = AlertDetailUiState(alert = alert),
          onReportSighting = { reportClicked = true },
      )
    }

    composeTestRule.onNodeWithTag(AlertDetailTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AlertDetailTestTags.PHOTO).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AlertDetailTestTags.NAME).assertTextEquals("Luna")
    composeTestRule.onNodeWithTag(AlertDetailTestTags.SPECIES).assertTextEquals("Cat")
    composeTestRule.onNodeWithTag(AlertDetailTestTags.LOST_AT).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AlertDetailTestTags.MAP).performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag(AlertDetailTestTags.LOCATION).assertTextEquals("46.5197, 6.6323")
    composeTestRule
        .onNodeWithTag("${AlertDetailTestTags.ALLERGY_PREFIX}0")
        .performScrollTo()
        .assertTextEquals("Chicken")
    composeTestRule
        .onNodeWithTag("${AlertDetailTestTags.BEHAVIOR_PREFIX}0")
        .performScrollTo()
        .assertTextEquals("Doesn't like being touched")
    composeTestRule
        .onNodeWithTag("${AlertDetailTestTags.BEHAVIOR_PREFIX}1")
        .performScrollTo()
        .assertTextEquals("Fearful of strangers")
    composeTestRule
        .onNodeWithTag("${AlertDetailTestTags.BEHAVIOR_PREFIX}2")
        .performScrollTo()
        .assertTextEquals("Reactive to other animals")
    composeTestRule
        .onNodeWithTag("${AlertDetailTestTags.BEHAVIOR_PREFIX}3")
        .performScrollTo()
        .assertTextEquals("Stressed in crowds")

    composeTestRule
        .onNodeWithTag(AlertDetailTestTags.REPORT_SIGHTING)
        .assertIsDisplayed()
        .performClick()
    assertTrue(reportClicked)
  }

  @Test
  fun errorState_showsFailureMessage() {
    composeTestRule.setContent {
      AlertDetailContent(state = AlertDetailUiState(errorMessage = "Couldn't load the alert."))
    }

    composeTestRule
        .onNodeWithTag(AlertDetailTestTags.ERROR)
        .assertIsDisplayed()
        .assertTextEquals("Couldn't load the alert.")
  }

  @Test
  fun loadingAndNotFoundStates_showTheirMessages() {
    val state = mutableStateOf(AlertDetailUiState(isLoading = true))
    composeTestRule.setContent { AlertDetailContent(state = state.value) }
    composeTestRule.onNodeWithTag(AlertDetailTestTags.LOADING).assertIsDisplayed()

    composeTestRule.runOnIdle { state.value = AlertDetailUiState() }
    composeTestRule.onNodeWithTag(AlertDetailTestTags.LOADING).assertIsDisplayed()

    composeTestRule.runOnIdle { state.value = AlertDetailUiState(notFound = true) }
    composeTestRule.onNodeWithTag(AlertDetailTestTags.NOT_FOUND).assertIsDisplayed()
  }

  @Test
  fun missingPhotoAndPetNotes_showPlaceholders() {
    val alert =
        Alert(
            id = "alert-2",
            petId = "pet-2",
            ownerId = "owner-1",
            lastKnownLocation = Location(46.5, 6.6),
            lostAtMillis = 1_700_000_000_000L,
            petName = "Milo",
            petSpecies = Species.OTHER,
        )

    val state = mutableStateOf(AlertDetailUiState(alert = alert))
    composeTestRule.setContent { AlertDetailContent(state = state.value) }

    composeTestRule.onNodeWithTag(AlertDetailTestTags.PHOTO_PLACEHOLDER).assertIsDisplayed()
    composeTestRule.onNodeWithText("Other").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("None listed").assertCountEquals(2)

    composeTestRule.runOnIdle {
      state.value = AlertDetailUiState(alert = alert.copy(petSpecies = Species.DOG))
    }
    composeTestRule.onNodeWithText("Dog").assertIsDisplayed()
  }

  @Test
  fun distance_isShownOnlyWhenKnown() {
    val alert =
        Alert(
            id = "alert-3",
            petId = "pet-3",
            ownerId = "owner-1",
            lastKnownLocation = Location(46.5, 6.6),
            lostAtMillis = 1_700_000_000_000L,
            petName = "Nala",
            petSpecies = Species.DOG,
        )
    val state = mutableStateOf(AlertDetailUiState(alert = alert, distanceText = "600 m away"))
    composeTestRule.setContent { AlertDetailContent(state = state.value) }

    composeTestRule.onNodeWithTag(AlertDetailTestTags.DISTANCE).assertTextEquals("600 m away")

    composeTestRule.runOnIdle { state.value = AlertDetailUiState(alert = alert) }
    composeTestRule.onNodeWithTag(AlertDetailTestTags.DISTANCE).assertDoesNotExist()
  }

  @Test
  fun lostTime_isFormattedForRecentAlerts() {
    val now = System.currentTimeMillis()
    val alert =
        Alert(
            id = "alert-recent",
            petId = "pet-recent",
            ownerId = "owner-1",
            lastKnownLocation = Location(46.5, 6.6),
            lostAtMillis = now,
            petName = "Milo",
            petSpecies = Species.DOG,
        )
    val state = mutableStateOf(AlertDetailUiState(alert = alert))
    composeTestRule.setContent { AlertDetailContent(state = state.value) }

    composeTestRule
        .onNodeWithTag(AlertDetailTestTags.LOST_AT)
        .assertTextContains("Lost just now", substring = true)

    composeTestRule.runOnIdle {
      state.value = AlertDetailUiState(alert = alert.copy(lostAtMillis = now - 20 * 60_000L))
    }
    composeTestRule
        .onNodeWithTag(AlertDetailTestTags.LOST_AT)
        .assertTextContains("Lost 20 min ago", substring = true)
  }
}
