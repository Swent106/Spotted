// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.HomeNavigation
import com.android.spotted.Route
import com.android.spotted.homeDestinations
import com.android.spotted.ui.home.HomeScreenContent
import com.android.spotted.ui.home.HomeScreenTestTags
import com.android.spotted.ui.home.HomeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Home → Settings → Back, through the app's real routes ([homeDestinations]). Home is drawn from a
 * hand-built state; Settings is the real [SettingsScreen] with its real ViewModel, as it needs no
 * repository.
 */
@RunWith(AndroidJUnit4::class)
class SettingsNavigationTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var navController: NavHostController
  private lateinit var homeNavigation: HomeNavigation
  private var settingsBack: () -> Unit = {}

  @Before
  fun setUp() {
    composeTestRule.setContent {
      navController = rememberNavController()
      NavHost(navController = navController, startDestination = Route.HOME) {
        homeDestinations(
            navController,
            homeContent = { navigation ->
              homeNavigation = navigation
              HomeScreenContent(
                  state = HomeUiState(),
                  onSettingsClick = navigation.onSettingsClick,
              )
            },
            settingsContent = { onBack ->
              settingsBack = onBack
              SettingsScreen(onBack = onBack)
            },
        )
      }
    }
    composeTestRule.waitForIdle()
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  private fun click(tag: String) {
    node(tag).performClick()
    composeTestRule.waitForIdle()
  }

  @Test
  fun settingsButton_opensSettings_withoutComingSoonMessage() {
    click(HomeScreenTestTags.SETTINGS_BUTTON)

    assertEquals(Route.SETTINGS, navController.currentDestination?.route)
    node(SettingsScreenTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithText(SettingsScreenTexts.TITLE).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(COMING_SOON, substring = true, ignoreCase = true, useUnmergedTree = true)
        .assertDoesNotExist()
  }

  @Test
  fun backFromSettings_returnsHome() {
    click(HomeScreenTestTags.SETTINGS_BUTTON)

    click(SettingsScreenTestTags.BACK_BUTTON)

    assertEquals(Route.HOME, navController.currentDestination?.route)
    node(HomeScreenTestTags.SCREEN).assertIsDisplayed()
    node(SettingsScreenTestTags.SCREEN).assertDoesNotExist()
    assertNull(navController.previousBackStackEntry)
  }

  @Test
  fun doubleTapOnSettings_opensItOnce() {
    // Both taps reach the app before the transition ends, like a fast double tap
    composeTestRule.runOnUiThread {
      homeNavigation.onSettingsClick()
      homeNavigation.onSettingsClick()
    }
    composeTestRule.waitForIdle()

    assertEquals(Route.SETTINGS, navController.currentDestination?.route)
    // Only one Settings was pushed on top of Home: one Back returns Home
    assertEquals(Route.HOME, navController.previousBackStackEntry?.destination?.route)
  }

  @Test
  fun quickBackPresses_returnHomeWithoutLeavingIt() {
    click(HomeScreenTestTags.SETTINGS_BUTTON)

    composeTestRule.runOnUiThread { repeat(3) { settingsBack() } }
    composeTestRule.waitForIdle()

    assertEquals(Route.HOME, navController.currentDestination?.route)
    node(HomeScreenTestTags.SCREEN).assertIsDisplayed()
  }

  private companion object {
    const val COMING_SOON = "coming soon"
  }
}
