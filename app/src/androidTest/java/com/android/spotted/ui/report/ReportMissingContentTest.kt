// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.report

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.Species
import com.android.spotted.ui.components.MapPickerTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI tests for [ReportMissingContent], driven by hand-made states and recording callbacks. */
@RunWith(AndroidJUnit4::class)
class ReportMissingContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val milo =
      Pet(
          id = "milo",
          ownerId = "owner",
          name = "Milo",
          species = Species.CAT,
          breed = "Orange tabby",
      )
  private val luna = Pet(id = "luna", ownerId = "owner", name = "Luna", species = Species.CAT)
  private val parcBertrand = Location(latitude = 46.1925, longitude = 6.1580)
  private val ready =
      ReportMissingUiState(pets = listOf(milo), selectedPetId = "milo", location = parcBertrand)

  private fun petTag(id: String) = "${ReportMissingScreenTestTags.PET_OPTION_PREFIX}$id"

  @Test
  fun showsPetsMapAndADisabledPublishButton() {
    composeTestRule.setContent {
      ReportMissingContent(state = ReportMissingUiState(pets = listOf(milo, luna)))
    }

    composeTestRule.onNodeWithTag(petTag("milo")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(petTag("luna")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MapPickerTestTags.MAP).performScrollTo().assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(ReportMissingScreenTestTags.PUBLISH_BUTTON)
        .performScrollTo()
        .assertIsNotEnabled()
  }

  @Test
  fun tappingAPet_reportsItsId() {
    var selected: String? = null
    composeTestRule.setContent {
      ReportMissingContent(
          state = ReportMissingUiState(pets = listOf(milo, luna)),
          onPetSelected = { selected = it },
      )
    }

    composeTestRule.onNodeWithTag(petTag("luna")).performClick()

    assertEquals("luna", selected)
  }

  @Test
  fun petAndLocationChosen_publishIsEnabledAndReportsTheTap() {
    var published = false
    composeTestRule.setContent {
      ReportMissingContent(state = ready, onPublish = { published = true })
    }

    composeTestRule
        .onNodeWithTag(ReportMissingScreenTestTags.PUBLISH_BUTTON)
        .performScrollTo()
        .assertIsEnabled()
        .performClick()

    assertTrue(published)
  }

  @Test
  fun whilePublishing_showsTheLoaderAndDisablesTheButton() {
    composeTestRule.setContent { ReportMissingContent(state = ready.copy(isPublishing = true)) }

    composeTestRule
        .onNodeWithTag(ReportMissingScreenTestTags.PUBLISH_BUTTON)
        .performScrollTo()
        .assertIsNotEnabled()
    composeTestRule
        .onNodeWithTag(ReportMissingScreenTestTags.PUBLISH_LOADER, useUnmergedTree = true)
        .assertIsDisplayed()
  }

  @Test
  fun noPets_showsTheEmptyMessage() {
    composeTestRule.setContent { ReportMissingContent(state = ReportMissingUiState()) }

    composeTestRule.onNodeWithTag(ReportMissingScreenTestTags.NO_PETS).assertIsDisplayed()
  }

  @Test
  fun error_isShownAndOkDismissesIt() {
    var dismissed = false
    composeTestRule.setContent {
      ReportMissingContent(
          state = ready.copy(errorMessage = ReportMissingViewModel.PUBLISH_FAILED),
          onDismissError = { dismissed = true },
      )
    }

    composeTestRule
        .onNodeWithTag(ReportMissingScreenTestTags.ERROR_MESSAGE)
        .performScrollTo()
        .assertIsDisplayed()
    composeTestRule.onNodeWithText("OK").performClick()

    assertTrue(dismissed)
  }

  @Test
  fun back_reportsTheTap() {
    var back = false
    composeTestRule.setContent {
      ReportMissingContent(state = ReportMissingUiState(), onBack = { back = true })
    }

    composeTestRule.onNodeWithTag(ReportMissingScreenTestTags.BACK_BUTTON).performClick()

    assertTrue(back)
  }
}
