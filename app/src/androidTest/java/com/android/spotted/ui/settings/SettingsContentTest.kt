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
import com.android.spotted.ui.theme.SpottedTheme
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
  private var deleteAccountClicks = 0

  private fun setContent(state: SettingsUiState = SettingsUiState()) {
    composeTestRule.setContent {
      SettingsContent(
          state = state,
          onBack = { backClicks++ },
          onSignOut = { signOutClicks++ },
          onDeleteAccount = { deleteAccountClicks++ },
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
                SettingsScreenTestTags.DELETE_ACCOUNT,
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
  fun deleteAccountButton_isShownAndEnabled() {
    setContent()

    node(SettingsScreenTestTags.DELETE_ACCOUNT)
        .assertIsDisplayed()
        .assertIsEnabled()
        .assertHasClickAction()
    composeTestRule.onNodeWithText(SettingsScreenTexts.DELETE_ACCOUNT).assertIsDisplayed()
  }

  @Test
  fun deleteAccountButton_callsOnDeleteAccountOnce_andNothingElse() {
    setContent()

    node(SettingsScreenTestTags.DELETE_ACCOUNT).performClick()

    assertEquals(1, deleteAccountClicks)
    assertEquals(0, signOutClicks)
    assertEquals(0, backClicks)
  }

  @Test
  fun deleteAccountButton_whileLeaving_isDisabledAndIgnoresClicks() {
    setContent(SettingsUiState(isLeaving = true))

    node(SettingsScreenTestTags.DELETE_ACCOUNT).assertIsDisplayed().assertIsNotEnabled()
    node(SettingsScreenTestTags.DELETE_ACCOUNT).performClick()

    assertEquals(0, deleteAccountClicks)
  }

  @Test
  fun deleteFailure_showsTheErrorMessage_andTheButtonStaysEnabled() {
    setContent(SettingsUiState(errorMessage = SettingsViewModel.DELETE_FAILED))

    node(SettingsScreenTestTags.ERROR)
        .assertIsDisplayed()
        .assertTextEquals(SettingsViewModel.DELETE_FAILED)
    node(SettingsScreenTestTags.DELETE_ACCOUNT).assertIsEnabled()
  }

  // ===================== Delete confirmation =====================

  private var confirms = 0
  private var cancels = 0

  private fun setConfirmDialog() {
    composeTestRule.setContent {
      SpottedTheme {
        DeleteAccountConfirmDialog(onConfirm = { confirms++ }, onCancel = { cancels++ })
      }
    }
  }

  @Test
  fun deleteConfirmation_showsTheWarningAndBothButtons() {
    setConfirmDialog()

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.DELETE_CONFIRM_DIALOG).assertIsDisplayed()
    composeTestRule.onNodeWithText("Delete your account?").assertIsDisplayed()
    composeTestRule
        .onNodeWithText("Your profile, pets and alerts will be deleted. This can't be undone.")
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.DELETE_CONFIRM).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.DELETE_CANCEL).assertIsDisplayed()
  }

  @Test
  fun deleteConfirmation_cancel_deletesNothing() {
    setConfirmDialog()

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.DELETE_CANCEL).performClick()

    assertEquals(1, cancels)
    assertEquals(0, confirms)
  }

  @Test
  fun deleteConfirmation_delete_confirmsOnce() {
    setConfirmDialog()

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.DELETE_CONFIRM).performClick()

    assertEquals(1, confirms)
    assertEquals(0, cancels)
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
