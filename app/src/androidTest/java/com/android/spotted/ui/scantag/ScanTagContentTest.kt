// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.scantag

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.tag.NfcAvailability
import com.android.spotted.ui.theme.SpottedTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ScanTagContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun waiting_showsNfcInstructionsAndAlternativeReportAction() {
    var reportAlertId: String? = "not-called"
    composeTestRule.setContent {
      ScanTagContent(
          state = ScanTagUiState(),
          onReportSighting = { reportAlertId = it },
      )
    }

    composeTestRule.onNodeWithTag(ScanTagScreenTestTags.WAITING).assertIsDisplayed()
    composeTestRule.onNodeWithText("Waiting for a tag…").assertIsDisplayed()
    composeTestRule.onNodeWithTag(ScanTagScreenTestTags.REPORT_SIGHTING).performClick()

    assertEquals(null, reportAlertId)
  }

  @Test
  fun loading_showsProgressMessage() {
    composeTestRule.setContent { ScanTagContent(ScanTagUiState(step = ScanTagStep.Loading)) }

    composeTestRule.onNodeWithTag(ScanTagScreenTestTags.LOADING).assertIsDisplayed()
    composeTestRule.onNodeWithText("Checking this tag").assertIsDisplayed()
  }

  @Test
  fun found_showsPetHealthDetailsAndContactActions() {
    var calledPhone: String? = null
    var emailedAddress: String? = null
    composeTestRule.setContent {
      ScanTagContent(
          state =
              ScanTagUiState(
                  step = ScanTagStep.Found,
                  petName = "Luna",
                  species = Species.CAT,
                  allergies = listOf("Chicken"),
                  behaviors = setOf(Behavior.FEARFUL_OF_STRANGERS),
                  visiblePhone = "+41 79 123 45 67",
                  visibleEmail = "owner@example.com",
              ),
          onCall = { calledPhone = it },
          onEmail = { emailedAddress = it },
      )
    }

    composeTestRule.onNodeWithTag(ScanTagScreenTestTags.FOUND).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ScanTagScreenTestTags.PET_NAME).assertTextEquals("Luna")
    composeTestRule
        .onNodeWithTag(ScanTagScreenTestTags.PHOTO_PLACEHOLDER)
        .assertTextEquals("Photo unavailable")
    composeTestRule.onNodeWithTag(ScanTagScreenTestTags.ALLERGIES).assertTextContains("Chicken")
    composeTestRule
        .onNodeWithTag(ScanTagScreenTestTags.BEHAVIORS)
        .assertTextContains("Fearful of strangers")
    composeTestRule.onNodeWithTag(ScanTagScreenTestTags.PHONE).assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag(ScanTagScreenTestTags.EMAIL).assertIsDisplayed().performClick()

    assertEquals("+41 79 123 45 67", calledPhone)
    assertEquals("owner@example.com", emailedAddress)
  }

  @Test
  fun foundLostPet_reportsSightingForItsAlert() {
    var reportAlertId: String? = null
    composeTestRule.setContent {
      ScanTagContent(
          state =
              ScanTagUiState(step = ScanTagStep.Found, petName = "Luna", openAlertId = "alert-1"),
          onReportSighting = { reportAlertId = it },
      )
    }

    composeTestRule.onNodeWithTag(ScanTagScreenTestTags.REPORT_SIGHTING).performClick()

    assertEquals("alert-1", reportAlertId)
  }

  @Test
  fun unknownTag_showsExplanationAndReportingActions() {
    var reportAlertId: String? = "not-called"
    var retried = false
    composeTestRule.setContent {
      ScanTagContent(
          state = ScanTagUiState(step = ScanTagStep.Unknown),
          onReportSighting = { reportAlertId = it },
          onRetry = { retried = true },
      )
    }

    composeTestRule.onNodeWithTag(ScanTagScreenTestTags.UNKNOWN).assertIsDisplayed()
    composeTestRule.onNodeWithText("This tag isn’t linked to a pet").assertIsDisplayed()
    composeTestRule.onNodeWithTag(ScanTagScreenTestTags.REPORT_SIGHTING).performClick()
    composeTestRule.onNodeWithTag(ScanTagScreenTestTags.RETRY).performClick()

    assertEquals(null, reportAlertId)
    assertEquals(true, retried)
  }

  @Test
  fun nfcDisabled_showsSettingsActionAndCancel() {
    var openedSettings = false
    var cancelled = false
    composeTestRule.setContent {
      ScanTagContent(
          state = ScanTagUiState(nfcAvailability = NfcAvailability.DISABLED),
          onOpenNfcSettings = { openedSettings = true },
          onCancel = { cancelled = true },
      )
    }

    composeTestRule.onNodeWithTag(ScanTagScreenTestTags.NFC_OFF).assertIsDisplayed()
    composeTestRule.onNodeWithText("NFC is off").assertIsDisplayed()
    composeTestRule.onNodeWithTag(ScanTagScreenTestTags.OPEN_NFC_SETTINGS).performClick()
    composeTestRule.onNodeWithTag(ScanTagScreenTestTags.CANCEL).performClick()

    assertEquals(true, openedSettings)
    assertEquals(true, cancelled)
  }

  @Test
  fun error_showsRetryAction() {
    var retried = false
    composeTestRule.setContent {
      ScanTagContent(
          state = ScanTagUiState(step = ScanTagStep.Error),
          onRetry = { retried = true },
      )
    }

    composeTestRule.onNodeWithTag(ScanTagScreenTestTags.ERROR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ScanTagScreenTestTags.RETRY).performClick()

    assertEquals(true, retried)
  }

  @Test
  fun waitingScreen_usesDarkTheme() {
    composeTestRule.setContent {
      SpottedTheme(darkTheme = true) { ScanTagContent(state = ScanTagUiState()) }
    }

    composeTestRule.onNodeWithTag(ScanTagScreenTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ScanTagScreenTestTags.WAITING).assertIsDisplayed()
  }
}
