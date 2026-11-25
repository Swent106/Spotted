// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.settings

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

  private fun setContent() {
    composeTestRule.setContent {
      SettingsContent(state = SettingsUiState(), onBack = { backClicks++ })
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
        tags.containsAll(listOf(SettingsScreenTestTags.SCREEN, SettingsScreenTestTags.BACK_BUTTON))
    )
    tags.forEach { tag ->
      assertTrue("\"$tag\" should start with settings_", tag.startsWith("settings_"))
    }
  }

  /** Every test tag set on the node of [root] or below it. */
  private fun collectTags(root: SemanticsNodeInteraction): List<String> {
    fun walk(node: SemanticsNode): List<String> =
        listOfNotNull(node.config.getOrNull(SemanticsProperties.TestTag)) +
            node.children.flatMap(::walk)
    return walk(root.fetchSemanticsNode())
  }
}
