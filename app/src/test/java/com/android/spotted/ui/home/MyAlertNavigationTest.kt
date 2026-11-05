// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.Route
import com.android.spotted.homeDestinations
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Home → My alert → Back, through the app's real routes, with stand-ins for the screens. */
@RunWith(AndroidJUnit4::class)
class MyAlertNavigationTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var navController: NavHostController

  @Before
  fun setUp() {
    composeTestRule.setContent {
      navController = rememberNavController()
      NavHost(navController = navController, startDestination = Route.HOME) {
        homeDestinations(
            navController,
            homeContent = { navigation ->
              HomeScreenContent(
                  state = HomeUiState(ownLostPets = listOf(ALERT)),
                  onMissingPetClick = navigation.onMissingPetClick,
              )
            },
            // The real screen needs Firebase: its alert id and Back are what matter here
            myAlertContent = { alertId, onBack ->
              Column {
                Text("My alert $alertId", Modifier.testTag(MY_ALERT_SCREEN))
                Button(onClick = onBack, modifier = Modifier.testTag(MY_ALERT_BACK)) {
                  Text("Back")
                }
              }
            },
        )
      }
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun missingPetBanner_opensMyAlert_andBackReturnsHome() {
    composeTestRule.onNodeWithTag(HomeScreenTestTags.missingBanner(ALERT.id)).performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("My alert ${ALERT.id}").assertIsDisplayed()
    assertEquals(Route.MY_ALERT, navController.currentDestination?.route)

    composeTestRule.onNodeWithTag(MY_ALERT_BACK).performClick()
    composeTestRule.waitForIdle()

    assertEquals(Route.HOME, navController.currentDestination?.route)
    composeTestRule.onNodeWithTag(HomeScreenTestTags.missingBanner(ALERT.id)).assertIsDisplayed()
  }

  private companion object {
    const val MY_ALERT_SCREEN = "test_myAlertScreen"
    const val MY_ALERT_BACK = "test_myAlertBack"
    val ALERT =
        Alert(
            id = "alert-milo",
            petId = "milo",
            ownerId = "me",
            lastKnownLocation = Location(46.2, 6.1),
            lostAtMillis = 1_700_000_000_000L,
            petName = "Milo",
            petSpecies = Species.CAT,
        )
  }
}
