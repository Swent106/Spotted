// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.components

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.Species
import com.android.spotted.ui.petprofile.PetProfileContent
import com.android.spotted.ui.petprofile.PetProfileScreenTestTags
import com.android.spotted.ui.petprofile.PetProfileUiState
import com.android.spotted.ui.profile.AccountOptionsDialog
import com.android.spotted.ui.profile.HelperProfileTestTags
import com.android.spotted.ui.sighting.ReportSightingContent
import com.android.spotted.ui.sighting.ReportSightingScreenTestTags
import com.android.spotted.ui.sighting.ReportSightingUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The screens that had no back button get the shared one, and Sign out is easy to tap. */
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
  fun petProfile_topBar_showsNameAndGoesBack() {
    val pet = Pet(id = "luna", ownerId = "me", name = "Luna", species = Species.CAT)
    composeTestRule.setContent {
      PetProfileContent(
          state = PetProfileUiState(pet = pet, isLoading = false),
          onBack = { backs++ },
      )
    }

    // Once in the top bar, once under the photo
    assertEquals(2, composeTestRule.onAllNodesWithText("Luna").fetchSemanticsNodes().size)
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.BACK_BUTTON).performClick()

    assertEquals(1, backs)
  }

  @Test
  fun signOut_is48dpHigh() {
    composeTestRule.setContent {
      AccountOptionsDialog(onSignOut = {}, onDeleteAccount = {}, onDismiss = {})
    }

    composeTestRule.onNodeWithTag(HelperProfileTestTags.SIGN_OUT).assertHeightIsAtLeast(48.dp)
    composeTestRule.onNodeWithText("Sign out").assertExists()
  }
}
