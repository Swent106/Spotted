// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Checks the Add pets step of onboarding: Personal info → Add pets → Home. The Add pets screen is
 * replaced by two buttons, so the test does not need the real ViewModel or Firebase.
 */
class AddPetsNavigationTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var navController: NavHostController

  private fun setNavHost() {
    composeTestRule.setContent {
      navController = rememberNavController()
      NavHost(navController = navController, startDestination = Route.PERSONAL_INFO) {
        composable(Route.PERSONAL_INFO) {
          Button(
              onClick = { navController.navigate(Route.ADD_PETS) },
              modifier = Modifier.testTag(PERSONAL_INFO),
          ) {
            Text("Continue")
          }
        }
        addPetsDestination(navController) { onBack, onDone ->
          Column {
            Button(onClick = onBack, modifier = Modifier.testTag(BACK)) { Text("Back") }
            Button(onClick = onDone, modifier = Modifier.testTag(DONE)) { Text("Done") }
          }
        }
        composable(Route.HOME) { Text("Home", Modifier.testTag(HOME)) }
      }
    }
    composeTestRule.onNodeWithTag(PERSONAL_INFO).performClick()
    composeTestRule.onNodeWithTag(DONE).assertIsDisplayed()
  }

  @Test
  fun back_returnsToPersonalInfo() {
    setNavHost()

    composeTestRule.onNodeWithTag(BACK).performClick()

    composeTestRule.onNodeWithTag(PERSONAL_INFO).assertIsDisplayed()
  }

  @Test
  fun done_opensHome_andClearsOnboardingFromTheBackStack() {
    setNavHost()

    composeTestRule.onNodeWithTag(DONE).performClick()

    composeTestRule.onNodeWithTag(HOME).assertIsDisplayed()
    composeTestRule.runOnIdle {
      assertEquals(Route.HOME, navController.currentDestination?.route)
      assertNull(navController.previousBackStackEntry)
    }
  }

  private companion object {
    const val PERSONAL_INFO = "test_personalInfo"
    const val BACK = "test_back"
    const val DONE = "test_done"
    const val HOME = "test_home"
  }
}
