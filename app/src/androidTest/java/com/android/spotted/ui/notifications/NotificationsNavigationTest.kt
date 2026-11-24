// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.notifications

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
import com.android.spotted.model.notification.SightingNotification
import com.android.spotted.ui.home.HomeScreenContent
import com.android.spotted.ui.home.HomeScreenTestTags
import com.android.spotted.ui.home.HomeUiState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Home bell → Notifications → a sighting → My alert, then Back, through the app's real routes.
 * Notifications is drawn from a hand-built state; My alert is a stand-in, as it needs Firebase.
 */
@RunWith(AndroidJUnit4::class)
class NotificationsNavigationTest {

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
                  state = HomeUiState(),
                  onNotificationsClick = navigation.onNotificationsClick,
              )
            },
            notificationsContent = { onBack, onOpenAlert ->
              NotificationsContent(
                  state = NotificationsUiState(isLoading = false, items = listOf(MILO)),
                  nowMillis = MILO.seenAtMillis,
                  onOpen = { onOpenAlert(it.alertId) },
                  onBack = onBack,
              )
            },
            myAlertContent = { alertId, onBack ->
              Column {
                Text("My alert $alertId")
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

  private fun click(tag: String) {
    composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).performClick()
    composeTestRule.waitForIdle()
  }

  @Test
  fun bell_opensNotifications_andASightingOpensMyAlert() {
    click(HomeScreenTestTags.NOTIFICATIONS_BUTTON)
    assertEquals(Route.NOTIFICATIONS, navController.currentDestination?.route)

    click(NotificationsScreenTestTags.item(MILO.sightingId))
    assertEquals(Route.MY_ALERT, navController.currentDestination?.route)
    composeTestRule.onNodeWithText("My alert ${MILO.alertId}").assertIsDisplayed()

    // Back from My alert returns to the list, not to Home
    click(MY_ALERT_BACK)
    assertEquals(Route.NOTIFICATIONS, navController.currentDestination?.route)
  }

  @Test
  fun backFromNotifications_returnsHome() {
    click(HomeScreenTestTags.NOTIFICATIONS_BUTTON)

    click(NotificationsScreenTestTags.BACK_BUTTON)

    assertEquals(Route.HOME, navController.currentDestination?.route)
  }

  private companion object {
    const val MY_ALERT_BACK = "test_myAlertBack"
    val MILO =
        SightingNotification(
            sightingId = "s-milo",
            alertId = "alert-milo",
            petName = "Milo",
            sightingPhotoUrl = null,
            seenAtMillis = 1_700_000_000_000L,
        )
  }
}
