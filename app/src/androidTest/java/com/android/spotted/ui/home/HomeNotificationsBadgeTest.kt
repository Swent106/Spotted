// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The badge on the Home bell: hidden at 0, the count above, "9+" past 9. */
@RunWith(AndroidJUnit4::class)
class HomeNotificationsBadgeTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun show(pendingSightings: Int) {
    composeTestRule.setContent {
      HomeScreenContent(state = HomeUiState(pendingSightings = pendingSightings))
    }
  }

  private fun badge() =
      composeTestRule.onNodeWithTag(HomeScreenTestTags.NOTIFICATIONS_BADGE, useUnmergedTree = true)

  @Test
  fun noPendingSighting_hidesTheBadge() {
    show(0)

    badge().assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(HomeScreenTestTags.NOTIFICATIONS_BUTTON, useUnmergedTree = true)
        .assertIsDisplayed()
  }

  @Test
  fun pendingSightings_showTheCount() {
    show(3)

    badge().assertIsDisplayed().assertTextEquals("3")
  }

  @Test
  fun moreThanNine_shows9Plus() {
    show(12)

    badge().assertTextEquals("9+")
  }
}
