// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.spotted.ui.StartDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Checks [StartRouting]: it opens the screen for each new start destination, and recreating the
 * activity (emulated by [StateRestorationTester]) keeps the screen the user was on. Screens are
 * replaced by plain texts, so the test does not need the real ViewModels or Firebase.
 */
class StartRoutingTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val restorationTester = StateRestorationTester(composeTestRule)

  private var destination by mutableStateOf(StartDestination.SIGNIN)

  private lateinit var navController: NavHostController

  private fun setNavHost(start: StartDestination) {
    destination = start
    restorationTester.setContent {
      navController = rememberNavController()
      StartRouting(destination, navController)
      NavHost(navController = navController, startDestination = Route.SIGNIN) {
        composable(Route.SIGNIN) { Text("Sign in", Modifier.testTag(SIGNIN)) }
        composable(Route.PERSONAL_INFO) { Text("Personal info", Modifier.testTag(PERSONAL_INFO)) }
        composable(Route.ADD_PETS) { Text("Add pets", Modifier.testTag(ADD_PETS)) }
        composable(Route.HOME) { Text("Home", Modifier.testTag(HOME)) }
        composable(Route.MAP) { Text("Map", Modifier.testTag(MAP)) }
      }
    }
  }

  private fun navigate(route: String) = composeTestRule.runOnIdle { navController.navigate(route) }

  @Test
  fun newDestination_opensItsScreen_andClearsTheBackStack() {
    setNavHost(StartDestination.SIGNIN)
    composeTestRule.onNodeWithTag(SIGNIN).assertIsDisplayed()

    destination = StartDestination.HOME

    composeTestRule.onNodeWithTag(HOME).assertIsDisplayed()
    composeTestRule.runOnIdle { assertNull(navController.previousBackStackEntry) }
  }

  @Test
  fun signOut_returnsToSignIn_andClearsTheBackStack() {
    setNavHost(StartDestination.HOME)
    navigate(Route.MAP)

    destination = StartDestination.SIGNIN

    composeTestRule.onNodeWithTag(SIGNIN).assertIsDisplayed()
    composeTestRule.runOnIdle { assertNull(navController.previousBackStackEntry) }
  }

  @Test
  fun recreation_keepsTheCurrentScreen() {
    setNavHost(StartDestination.HOME)
    navigate(Route.MAP)
    composeTestRule.onNodeWithTag(MAP).assertIsDisplayed()

    restorationTester.emulateSavedInstanceStateRestore()

    composeTestRule.onNodeWithTag(MAP).assertIsDisplayed()
    composeTestRule.runOnIdle {
      assertEquals(Route.MAP, navController.currentDestination?.route)
      assertEquals(Route.HOME, navController.previousBackStackEntry?.destination?.route)
    }
  }

  @Test
  fun recreation_afterOnboarding_staysOnHome() {
    // Onboarding ends on Home while the start destination is still Personal info
    setNavHost(StartDestination.PERSONAL_INFO)
    navigate(Route.ADD_PETS)
    composeTestRule.runOnIdle {
      navController.navigate(Route.HOME) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
      }
    }
    composeTestRule.onNodeWithTag(HOME).assertIsDisplayed()

    restorationTester.emulateSavedInstanceStateRestore()

    composeTestRule.onNodeWithTag(HOME).assertIsDisplayed()
    composeTestRule.runOnIdle { assertNull(navController.previousBackStackEntry) }
  }

  private companion object {
    const val SIGNIN = "test_signIn"
    const val PERSONAL_INFO = "test_personalInfo"
    const val ADD_PETS = "test_addPets"
    const val HOME = "test_home"
    const val MAP = "test_map"
  }
}
