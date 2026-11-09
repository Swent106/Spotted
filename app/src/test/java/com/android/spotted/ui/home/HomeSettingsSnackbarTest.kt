// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Settings has no screen yet: the button says so instead of doing nothing (M1 feedback). */
@RunWith(AndroidJUnit4::class)
class HomeSettingsSnackbarTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun settingsButton_showsComingSoon() {
    var clicks = 0
    composeTestRule.setContent {
      HomeScreenContent(state = HomeUiState(), onSettingsClick = { clicks++ })
    }
    composeTestRule.onNodeWithText(HomeScreenTexts.SETTINGS_COMING_SOON).assertDoesNotExist()

    composeTestRule.onNodeWithTag(HomeScreenTestTags.SETTINGS_BUTTON).performClick()

    composeTestRule.onNodeWithText(HomeScreenTexts.SETTINGS_COMING_SOON).assertIsDisplayed()
    assertEquals(1, clicks)
  }
}
