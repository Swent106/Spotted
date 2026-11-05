package com.android.spotted.ui.home

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.android.spotted.HomeNavigation
import com.android.spotted.Route
import com.android.spotted.homeDestinations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Taps that arrive during a navigation transition are ignored (M1 bugs: two buttons at once, or
 * back pressed in quick succession). The taps are sent in a single UI-thread block, the way two
 * fingers or a fast series of presses reach the app before the transition ends.
 */
class NavigationDebounceTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var navController: NavHostController
  private lateinit var homeNavigation: HomeNavigation
  private var mapBack: () -> Unit = {}

  @Before
  fun setUp() {
    composeTestRule.setContent {
      navController = rememberNavController()
      NavHost(navController = navController, startDestination = Route.HOME) {
        homeDestinations(
            navController,
            homeContent = { navigation ->
              homeNavigation = navigation
              Text("Home", Modifier.testTag(HOME_SCREEN))
            },
            mapContent = { onBack, _ ->
              mapBack = onBack
              Text("Map", Modifier.testTag(MAP_SCREEN))
            },
            profileContent = { Text("Profile", Modifier.testTag(PROFILE_SCREEN)) },
        )
      }
    }
    composeTestRule.waitForIdle()
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  @Test
  fun twoHomeButtonsAtOnce_openOneScreen() {
    composeTestRule.runOnUiThread {
      homeNavigation.onMapClick()
      homeNavigation.onProfileClick()
    }
    composeTestRule.waitForIdle()

    node(MAP_SCREEN).assertIsDisplayed()
    assertEquals(Route.MAP, navController.currentDestination?.route)
    // Only Map was pushed on top of Home: one Back returns Home
    assertEquals(Route.HOME, navController.previousBackStackEntry?.destination?.route)
  }

  @Test
  fun fiveQuickBackPresses_returnHomeAndNeverBlank() {
    composeTestRule.runOnUiThread { homeNavigation.onMapClick() }
    composeTestRule.waitForIdle()
    node(MAP_SCREEN).assertIsDisplayed()

    composeTestRule.runOnUiThread { repeat(5) { mapBack() } }
    composeTestRule.waitForIdle()

    node(HOME_SCREEN).assertIsDisplayed()
    assertEquals(Route.HOME, navController.currentDestination?.route)
    assertNull(navController.previousBackStackEntry)
  }

  @Test
  fun backAfterTheTransition_stillWorks() {
    composeTestRule.runOnUiThread { homeNavigation.onMapClick() }
    composeTestRule.waitForIdle()

    composeTestRule.runOnUiThread { mapBack() }
    composeTestRule.waitForIdle()
    composeTestRule.runOnUiThread { homeNavigation.onProfileClick() }
    composeTestRule.waitForIdle()

    node(PROFILE_SCREEN).assertIsDisplayed()
    assertEquals(Route.PROFILE, navController.currentDestination?.route)
  }

  private companion object {
    const val HOME_SCREEN = "home_screen"
    const val MAP_SCREEN = "map_screen"
    const val PROFILE_SCREEN = "profile_screen"
  }
}
