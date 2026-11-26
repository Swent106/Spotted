// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.settings

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The stateless Settings screen, drawn from a hand-built state. */
@RunWith(AndroidJUnit4::class)
class SettingsContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  private var backClicks = 0
  private var signOutClicks = 0

  private fun setContent(state: SettingsUiState = SettingsUiState()) {
    composeTestRule.setContent {
      SettingsContent(
          state = state,
          onBack = { backClicks++ },
          onSignOut = { signOutClicks++ },
      )
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  @Test
  fun screen_showsTitleAndBackButton() {
    setContent()

    node(SettingsScreenTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithText(SettingsScreenTexts.TITLE).assertIsDisplayed()
    node(SettingsScreenTestTags.BACK_BUTTON)
        .assertIsDisplayed()
        .assertIsEnabled()
        .assertHasClickAction()
  }

  @Test
  fun backButton_callsOnBackOnce() {
    setContent()
    assertEquals(0, backClicks)

    node(SettingsScreenTestTags.BACK_BUTTON).performClick()

    assertEquals(1, backClicks)
  }

  @Test
  fun everyTestTag_startsWithSettingsPrefix() {
    setContent()

    val tags = collectTags(composeTestRule.onNode(isRoot(), useUnmergedTree = true))

    assertTrue(
        tags.containsAll(
            listOf(
                SettingsScreenTestTags.SCREEN,
                SettingsScreenTestTags.BACK_BUTTON,
                SettingsScreenTestTags.ACCOUNT_SECTION,
                SettingsScreenTestTags.SIGN_OUT,
            )
        )
    )
    tags.forEach { tag ->
      assertTrue("\"$tag\" should start with settings_", tag.startsWith("settings_"))
    }
  }

  // ===================== Account section =====================

  @Test
  fun accountSection_showsItsLabelAndAnEnabledSignOutButton() {
    setContent()

    node(SettingsScreenTestTags.ACCOUNT_SECTION).assertIsDisplayed()
    composeTestRule.onNodeWithText(SettingsScreenTexts.ACCOUNT).assertIsDisplayed()
    node(SettingsScreenTestTags.SIGN_OUT)
        .assertIsDisplayed()
        .assertIsEnabled()
        .assertHasClickAction()
    composeTestRule.onNodeWithText(SettingsScreenTexts.SIGN_OUT).assertIsDisplayed()
  }

  @Test
  fun signOutButton_callsOnSignOutOnce_andNotOnBack() {
    setContent()

    node(SettingsScreenTestTags.SIGN_OUT).performClick()

    assertEquals(1, signOutClicks)
    assertEquals(0, backClicks)
  }

  @Test
  fun signOutButton_whileLeaving_isDisabledAndIgnoresClicks() {
    setContent(SettingsUiState(isLeaving = true))

    node(SettingsScreenTestTags.SIGN_OUT).assertIsDisplayed().assertIsNotEnabled()
    node(SettingsScreenTestTags.SIGN_OUT).performClick()

    assertEquals(0, signOutClicks)
  }

  @Test
  fun signOutButton_hasAtLeast56DpTouchTarget() {
    setContent()

    val heightPx = node(SettingsScreenTestTags.SIGN_OUT).fetchSemanticsNode().boundsInRoot.height
    val heightDp = with(composeTestRule.density) { heightPx.toDp() }

    assertTrue("Sign-out touch target was $heightDp", heightDp >= 56.dp)
  }

  @Test
  fun signOutFailure_showsTheErrorMessage() {
    setContent(SettingsUiState(errorMessage = SettingsViewModel.SIGN_OUT_FAILED))

    node(SettingsScreenTestTags.ERROR)
        .assertIsDisplayed()
        .assertTextEquals(SettingsViewModel.SIGN_OUT_FAILED)
    // The user can try again
    node(SettingsScreenTestTags.SIGN_OUT).assertIsEnabled()
  }

  @Test
  fun noError_showsNoErrorMessage() {
    setContent()

    node(SettingsScreenTestTags.ERROR).assertDoesNotExist()
  }

  /** Every test tag set on the node of [root] or below it. */
  private fun collectTags(root: SemanticsNodeInteraction): List<String> {
    fun walk(node: SemanticsNode): List<String> =
        listOfNotNull(node.config.getOrNull(SemanticsProperties.TestTag)) +
            node.children.flatMap(::walk)
    return walk(root.fetchSemanticsNode())
  }
}
