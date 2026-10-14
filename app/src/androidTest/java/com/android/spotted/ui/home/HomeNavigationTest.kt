package com.android.spotted.ui.home

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.android.spotted.Route
import com.android.spotted.homeDestinations
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Navigation from Home, through the app's real routes ([homeDestinations]). Home is drawn from a
 * hand-built state, so no repository is needed.
 */
class HomeNavigationTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var navController: NavHostController

  @Before
  fun setUp() {
    composeTestRule.setContent {
      navController = rememberNavController()
      NavHost(navController = navController, startDestination = Route.HOME) {
        homeDestinations(navController) { navigation ->
          HomeScreenContent(
              state = STATE,
              onReportMissingClick = navigation.onReportMissingClick,
              onAlertClick = navigation.onAlertClick,
              onSeenPetClick = navigation.onSeenPetClick,
          )
        }
      }
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  /** Scrolls the lazy Home list until the node tagged [tag] is composed, then returns it. */
  private fun scrollTo(tag: String): SemanticsNodeInteraction {
    composeTestRule.onNodeWithTag(HomeScreenTestTags.BODY).performScrollToNode(hasTestTag(tag))
    return node(tag)
  }

  @Test
  fun tappingAnAlert_opensItsDetail() {
    scrollTo(HomeScreenTestTags.alertCard(NALA.id)).performClick()

    node(ALERT_DETAIL_SCREEN).assertIsDisplayed().assertTextEquals("Alert ${NALA.id}")
    assertEquals(Route.ALERT_DETAIL, navController.currentDestination?.route)
  }

  @Test
  fun seenButton_opensReportSightingForThatAlert() {
    scrollTo(HomeScreenTestTags.seenButton(MILO.id)).performClick()

    node(REPORT_SIGHTING_SCREEN)
        .assertIsDisplayed()
        .assertTextEquals("Report a sighting of ${MILO.id}")
  }

  @Test
  fun reportMissingButton_opensReportMissing() {
    // The button is the last item: scrolling to the end puts it above the tab bar, which would
    // otherwise receive the click when the button is just scrolled into view at the bottom
    repeat(3) {
      composeTestRule.onNodeWithTag(HomeScreenTestTags.BODY).performTouchInput { swipeUp() }
    }
    scrollTo(HomeScreenTestTags.REPORT_MISSING_BUTTON).performClick()

    node(REPORT_MISSING_SCREEN).assertIsDisplayed()
  }

  @Test
  fun back_fromAlertDetail_returnsHome() {
    scrollTo(HomeScreenTestTags.alertCard(MILO.id)).performClick()
    composeTestRule.runOnIdle { navController.popBackStack() }

    node(HomeScreenTestTags.SCREEN).assertIsDisplayed()
  }

  private companion object {
    const val ALERT_DETAIL_SCREEN = "alert_detail_screen"
    const val REPORT_SIGHTING_SCREEN = "report_sighting_screen"
    const val REPORT_MISSING_SCREEN = "report_missing_screen"

    val MILO =
        Alert(
            id = "milo",
            petId = "milo",
            ownerId = "someone",
            lastKnownLocation = Location(latitude = 46.2, longitude = 6.14),
            lostAtMillis = 0L,
            petName = "Milo",
            petSpecies = Species.CAT,
        )
    val NALA = MILO.copy(id = "nala", petId = "nala", petName = "Nala")

    val STATE = HomeUiState(lostPetsNearby = HomeSection(listOf(MILO, NALA)))
  }
}
