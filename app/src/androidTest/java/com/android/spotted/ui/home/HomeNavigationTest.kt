// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
        homeDestinations(
            navController,
            homeContent = { navigation ->
              HomeScreenContent(
                  state = STATE,
                  onReportMissingClick = navigation.onReportMissingClick,
                  onAlertClick = navigation.onAlertClick,
                  onSeenPetClick = navigation.onSeenPetClick,
                  onAddPetClick = navigation.onAddPetClick,
                  onPetClick = navigation.onPetClick,
                  onMapClick = navigation.onMapClick,
                  onCameraClick = navigation.onCameraClick,
                  onProfileClick = navigation.onProfileClick,
              )
            },
            // The real screen needs Firebase: a tagged text is enough to check the navigation
            reportMissingContent = {
              Text("Report missing", Modifier.testTag(REPORT_MISSING_SCREEN))
            },
            // Same for Alert detail: its id and its "Report a sighting" action are what matter
            alertDetailContent = { alertId, onReportSighting ->
              Column {
                Text("Alert $alertId", Modifier.testTag(ALERT_DETAIL_SCREEN))
                Button(
                    onClick = onReportSighting,
                    modifier = Modifier.testTag(ALERT_DETAIL_REPORT_SIGHTING),
                ) {
                  Text("Report a sighting")
                }
              }
            },
            addPetContent = { Text("Add pets", Modifier.testTag(ADD_PETS_SCREEN)) },
            reportSightingContent = { alertId, _ ->
              Text(
                  "Report a sighting of ${alertId ?: "a new pet"}",
                  Modifier.testTag(REPORT_SIGHTING_SCREEN),
              )
            },
            petProfileContent = { petId -> Text("Pet $petId", Modifier.testTag(PET_SCREEN)) },
            mapContent = { _, _ -> Text("Map", Modifier.testTag(MAP_SCREEN)) },
            profileContent = { Text("Profile", Modifier.testTag(PROFILE_SCREEN)) },
        )
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
    scrollTo(HomeScreenTestTags.REPORT_MISSING_BUTTON).performClick()

    node(REPORT_MISSING_SCREEN).assertIsDisplayed()
  }

  @Test
  fun reportSighting_fromAlertDetail_opensReportSightingForThatAlert() {
    scrollTo(HomeScreenTestTags.alertCard(NALA.id)).performClick()

    node(ALERT_DETAIL_REPORT_SIGHTING).performClick()

    node(REPORT_SIGHTING_SCREEN)
        .assertIsDisplayed()
        .assertTextEquals("Report a sighting of ${NALA.id}")
    assertEquals(Route.REPORT_SIGHTING, navController.currentDestination?.route)
  }

  @Test
  fun cameraTab_opensReportSightingWithoutAlert() {
    node(HomeScreenTestTags.TAB_CAMERA).performClick()

    node(REPORT_SIGHTING_SCREEN)
        .assertIsDisplayed()
        .assertTextEquals("Report a sighting of a new pet")
    assertEquals(Route.NEW_SIGHTING, navController.currentDestination?.route)
  }

  @Test
  fun profileTab_opensTheHelperProfile() {
    node(HomeScreenTestTags.TAB_PROFILE).performClick()

    node(PROFILE_SCREEN).assertIsDisplayed()
    assertEquals(Route.PROFILE, navController.currentDestination?.route)
  }

  @Test
  fun mapTab_opensTheMap() {
    node(HomeScreenTestTags.TAB_MAP).performClick()

    node(MAP_SCREEN).assertIsDisplayed()
    assertEquals(Route.MAP, navController.currentDestination?.route)
  }

  @Test
  fun addPetButton_opensAddPets() {
    scrollTo(HomeScreenTestTags.ADD_PET_BUTTON).performClick()

    node(ADD_PETS_SCREEN).assertIsDisplayed()
    assertEquals(Route.ADD_PET, navController.currentDestination?.route)
  }

  @Test
  fun back_fromAlertDetail_returnsHome() {
    scrollTo(HomeScreenTestTags.alertCard(MILO.id)).performClick()
    composeTestRule.runOnIdle { navController.popBackStack() }

    node(HomeScreenTestTags.SCREEN).assertIsDisplayed()
  }

  private companion object {
    const val ALERT_DETAIL_SCREEN = "alert_detail_screen"
    const val ALERT_DETAIL_REPORT_SIGHTING = "alert_detail_report_sighting"
    const val REPORT_SIGHTING_SCREEN = "report_sighting_screen"
    const val REPORT_MISSING_SCREEN = "report_missing_screen"
    const val ADD_PETS_SCREEN = "add_pets_screen"
    const val MAP_SCREEN = "map_screen"
    const val PET_SCREEN = "pet_screen"
    const val PROFILE_SCREEN = "profile_screen"

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
