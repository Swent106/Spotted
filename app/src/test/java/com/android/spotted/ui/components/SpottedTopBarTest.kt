// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.components

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.ui.theme.SpottedTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The shared top bar: one back button, 48 dp, that calls back once per tap. */
@RunWith(AndroidJUnit4::class)
class SpottedTopBarTest {

  @get:Rule val composeTestRule = createComposeRule()

  private var backs = 0

  private fun show(subtitle: String? = null) {
    composeTestRule.setContent {
      SpottedTheme {
        SpottedTopBar(
            title = "Report a pet missing",
            subtitle = subtitle,
            onBack = { backs++ },
            backTestTag = BACK,
        )
      }
    }
  }

  @Test
  fun backButton_callsBack() {
    show()

    composeTestRule.onNodeWithTag(BACK).performClick()

    assertEquals(1, backs)
  }

  @Test
  fun backButton_hasA48dpTouchTarget() {
    show()

    composeTestRule.onNodeWithTag(BACK).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
  }

  @Test
  fun showsTitleAndSubtitle() {
    show(subtitle = "Everyone within 3 km gets an alert")

    composeTestRule.onNodeWithText("Report a pet missing").assertIsDisplayed()
    composeTestRule.onNodeWithText("Everyone within 3 km gets an alert").assertIsDisplayed()
  }

  @Test
  fun disabledBackButton_doesNothing() {
    composeTestRule.setContent {
      SpottedTheme { SpottedBackButton(onClick = { backs++ }, testTag = BACK, enabled = false) }
    }

    composeTestRule.onNodeWithTag(BACK).performClick()

    assertEquals(0, backs)
  }

  private companion object {
    const val BACK = "test_back"
  }
}
