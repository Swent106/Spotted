// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.ui.home.HomeScreenTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HelperProfileContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val clicks = mutableMapOf<String, Int>()

  private fun click(name: String): () -> Unit = { clicks[name] = (clicks[name] ?: 0) + 1 }

  private fun setContent(state: HelperProfileUiState) {
    composeTestRule.setContent {
      HelperProfileContent(
          state = state,
          navigation =
              HelperProfileNavigation(
                  onHomeClick = click("home"),
                  onMapClick = click("map"),
                  onCameraClick = click("camera"),
                  onEditProfileClick = click("edit"),
              ),
          onAccountClick = click("account"),
      )
    }
  }

  @Test
  fun showsTheNameAreaAndCount() {
    setContent(HelperProfileUiState(name = "Rayen", homeArea = "Lausanne", petsBroughtHome = 4))

    composeTestRule.onNodeWithTag(HelperProfileTestTags.NAME).assertTextEquals("Rayen")
    composeTestRule
        .onNodeWithTag(HelperProfileTestTags.AREA)
        .assertTextEquals("Helping out in Lausanne")
    composeTestRule
        .onNodeWithTag(HelperProfileTestTags.PETS_BROUGHT_HOME)
        .assertTextEquals("4 pets brought home")
  }

  @Test
  fun profilePhoto_isShownOverTheInitial_onlyWhenThereIsOne() {
    setContent(HelperProfileUiState(name = "Rayen", photoUrl = "firestore://photos/profile_user-1"))

    composeTestRule
        .onNodeWithContentDescription("Profile photo", useUnmergedTree = true)
        .assertExists()
  }

  @Test
  fun noProfilePhoto_showsOnlyTheInitial() {
    setContent(HelperProfileUiState(name = "Rayen"))

    composeTestRule
        .onNodeWithContentDescription("Profile photo", useUnmergedTree = true)
        .assertDoesNotExist()
  }

  @Test
  fun buttonsCallTheirCallbacks() {
    setContent(HelperProfileUiState(name = "Rayen"))

    composeTestRule.onNodeWithTag(HelperProfileTestTags.ACCOUNT_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(HelperProfileTestTags.EDIT_PROFILE)
        .performScrollTo()
        .performClick()
    composeTestRule.onNodeWithTag(HomeScreenTestTags.TAB_HOME).performClick()
    composeTestRule.onNodeWithTag(HomeScreenTestTags.TAB_MAP).performClick()
    composeTestRule.onNodeWithTag(HomeScreenTestTags.TAB_CAMERA).performClick()

    assertEquals(mapOf("account" to 1, "edit" to 1, "home" to 1, "map" to 1, "camera" to 1), clicks)
  }

  @Test
  fun errorMessage_isShown() {
    setContent(HelperProfileUiState(errorMessage = HelperProfileViewModel.LOAD_FAILED))

    composeTestRule.onNodeWithTag(HelperProfileTestTags.ERROR).assertIsDisplayed()
  }

  @Test
  fun accountOptions_offerSignOutAndDelete() {
    var signOuts = 0
    var deletions = 0
    composeTestRule.setContent {
      AccountOptionsDialog(
          onSignOut = { signOuts++ },
          onDeleteAccount = { deletions++ },
          onDismiss = {},
      )
    }

    composeTestRule.onNodeWithTag(HelperProfileTestTags.SIGN_OUT).performClick()
    composeTestRule.onNodeWithTag(HelperProfileTestTags.DELETE_ACCOUNT).performClick()

    assertEquals(1, signOuts)
    assertEquals(1, deletions)
  }

  @Test
  fun signOutButton_hasAtLeast56DpTouchTarget() {
    composeTestRule.setContent {
      AccountOptionsDialog(onSignOut = {}, onDeleteAccount = {}, onDismiss = {})
    }

    val heightPx =
        composeTestRule
            .onNodeWithTag(HelperProfileTestTags.SIGN_OUT)
            .fetchSemanticsNode()
            .boundsInRoot
            .height
    val heightDp = with(composeTestRule.density) { heightPx.toDp() }

    assertTrue("Sign-out touch target was $heightDp", heightDp >= 56.dp)
  }

  @Test
  fun accountButtonVisual_isAtLeast48By32Dp() {
    setContent(HelperProfileUiState(name = "Rayen"))

    val bounds =
        composeTestRule
            .onNodeWithTag(HelperProfileTestTags.ACCOUNT_BUTTON_VISUAL, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
    val minWidthPx = with(composeTestRule.density) { 48.dp.roundToPx().toFloat() }
    val heightDp = with(composeTestRule.density) { bounds.height.toDp() }

    assertTrue("Account button width was ${bounds.width}px", bounds.width >= minWidthPx)
    assertTrue("Account button height was $heightDp", heightDp >= 32.dp)
  }
}
