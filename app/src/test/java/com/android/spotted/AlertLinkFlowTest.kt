// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertStatus
import com.android.spotted.model.alert.buildAlertLink
import com.android.spotted.model.fakes.FakeAlertRepository
import com.android.spotted.ui.StartDestination
import com.android.spotted.ui.alertdetail.AlertDetailScreen
import com.android.spotted.ui.alertdetail.AlertDetailTestTags
import com.android.spotted.ui.alertdetail.AlertDetailViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A received alert link (#476), from the intent to the screen: the app's real routes and Alert
 * detail, with the alerts in a fake repository. The signed-out case is in the androidTest
 * `AlertLinkTest`.
 */
@RunWith(AndroidJUnit4::class)
class AlertLinkFlowTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private lateinit var navController: NavHostController

  /** Opens the app on Home, signed in, as Android does when [link] is tapped. */
  private fun openLink(link: String) {
    val alerts = FakeAlertRepository()
    runBlocking {
      alerts.publishAlert(OPEN_ALERT)
      alerts.publishAlert(CLOSED_ALERT)
    }
    val route = routeOf(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
    composeTestRule.setContent {
      navController = rememberNavController()
      StartRouting(StartDestination.HOME, navController)
      NotificationRouting(route, StartDestination.HOME, navController) {}
      NavHost(navController = navController, startDestination = Route.SIGNIN) {
        composable(Route.SIGNIN) { Text("sign in") }
        homeDestinations(
            navController,
            homeContent = { Text("home", Modifier.testTag(HOME)) },
            alertDetailContent = { alertId, onReportSighting, onBack ->
              AlertDetailScreen(
                  AlertDetailViewModel(alerts),
                  alertId,
                  onReportSighting,
                  onBack = onBack,
              )
            },
        )
      }
    }
    composeTestRule.waitForIdle()
  }

  private fun linkTo(alertId: String) = buildAlertLink(alertId).getOrThrow()

  private fun assertShown(tag: String) = composeTestRule.onNodeWithTag(tag).assertIsDisplayed()

  @Test
  fun aLinkToAnOpenAlert_showsThatAlert() {
    openLink(linkTo(OPEN_ALERT.id))

    assertEquals(Route.ALERT_DETAIL, navController.currentDestination?.route)
    assertShown(AlertDetailTestTags.NAME)
    assertShown(AlertDetailTestTags.REPORT_SIGHTING)
  }

  @Test
  fun aLinkToAClosedAlert_saysThePetWasFound_withoutReportingASighting() {
    openLink(linkTo(CLOSED_ALERT.id))

    assertShown(AlertDetailTestTags.FOUND_MESSAGE)
    composeTestRule.onNodeWithTag(AlertDetailTestTags.REPORT_SIGHTING).assertDoesNotExist()
  }

  @Test
  fun aLinkToADeletedAlert_saysItIsNoLongerAvailable() {
    openLink(linkTo("deleted-alert"))

    assertShown(AlertDetailTestTags.NOT_FOUND)
  }

  @Test
  fun anInvalidLink_staysOnHome() {
    openLink("https://spotted-fd9bf.web.app/alert/a1?x=1")

    assertEquals(Route.HOME, navController.currentDestination?.route)
    assertShown(HOME)
  }

  @Test
  fun backFromALinkedAlert_goesHome() {
    openLink(linkTo(OPEN_ALERT.id))

    composeTestRule.onNodeWithTag(AlertDetailTestTags.BACK).performClick()
    composeTestRule.waitForIdle()

    assertEquals(Route.HOME, navController.currentDestination?.route)
    assertShown(HOME)
  }

  private companion object {
    const val HOME = "test_home"

    val OPEN_ALERT =
        Alert(
            id = "alert-open",
            petId = "milo",
            ownerId = "owner",
            lastKnownLocation = Location(46.52, 6.63),
            lostAtMillis = 0L,
            petName = "Milo",
            petSpecies = Species.CAT,
        )

    val CLOSED_ALERT =
        OPEN_ALERT.copy(
            id = "alert-closed",
            petId = "luna",
            petName = "Luna",
            status = AlertStatus.CLOSED,
            closedAtMillis = 1L,
        )
  }
}
