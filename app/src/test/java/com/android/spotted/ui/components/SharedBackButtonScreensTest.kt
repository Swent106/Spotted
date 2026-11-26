// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.Species
import com.android.spotted.ui.petprofile.PetProfileContent
import com.android.spotted.ui.petprofile.PetProfileScreenTestTags
import com.android.spotted.ui.petprofile.PetProfileUiState
import com.android.spotted.ui.sighting.ReportSightingContent
import com.android.spotted.ui.sighting.ReportSightingScreenTestTags
import com.android.spotted.ui.sighting.ReportSightingUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The screens that had no back button get the shared one. */
@RunWith(AndroidJUnit4::class)
class SharedBackButtonScreensTest {

  @get:Rule val composeTestRule = createComposeRule()

  private var backs = 0

  @Test
  fun reportSighting_backButton_goesBack() {
    composeTestRule.setContent {
      ReportSightingContent(state = ReportSightingUiState(), onBack = { backs++ })
    }

    composeTestRule.onNodeWithTag(ReportSightingScreenTestTags.BACK_BUTTON).performClick()

    assertEquals(1, backs)
  }

  @Test
  fun reportSighting_withoutBack_hasNoBackButton() {
    composeTestRule.setContent { ReportSightingContent(state = ReportSightingUiState()) }

    composeTestRule.onNodeWithTag(ReportSightingScreenTestTags.BACK_BUTTON).assertDoesNotExist()
  }

  @Test
  fun petProfile_topBar_showsFixedTitleAndGoesBack() {
    val pet = Pet(id = "luna", ownerId = "me", name = "Luna", species = Species.CAT)
    composeTestRule.setContent {
      PetProfileContent(
          state = PetProfileUiState(pet = pet, isLoading = false),
          onBack = { backs++ },
      )
    }

    // A fixed title, so the name is shown once, under the photo
    composeTestRule.onNodeWithText("Pet profile").assertIsDisplayed()
    assertEquals(1, composeTestRule.onAllNodesWithText("Luna").fetchSemanticsNodes().size)
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.BACK_BUTTON).performClick()

    assertEquals(1, backs)
  }
}
