// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted

import android.content.Intent
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.data.notification.SystemNotifier.Companion.EXTRA_ROUTE
import com.android.spotted.ui.StartDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A tapped notification opens its route, through [StartRouting] as in the real app. */
@RunWith(AndroidJUnit4::class)
class NotificationRoutingTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var navController: NavHostController
  private var destination by mutableStateOf(StartDestination.SIGNIN)
  private var route by mutableStateOf<String?>(null)
  private var opened = 0

  @Before
  fun setUp() {
    composeTestRule.setContent {
      navController = rememberNavController()
      StartRouting(destination, navController)
      NotificationRouting(route, destination, navController) {
        opened++
        route = null
      }
      NavHost(navController = navController, startDestination = Route.SIGNIN) {
        composable(Route.SIGNIN) { Text("sign in") }
        composable(Route.PERSONAL_INFO) { Text("personal info") }
        composable(Route.HOME) { Text("home") }
        composable(Route.MY_ALERT) { Text("my alert") }
      }
    }
    composeTestRule.waitForIdle()
  }

  private fun currentRoute() = navController.currentDestination?.route

  private fun update(block: () -> Unit) {
    composeTestRule.runOnIdle(block)
    composeTestRule.waitForIdle()
  }

  @Test
  fun onAColdStart_waitsForHome_thenOpensTheRouteAboveHome() {
    update { route = Route.myAlert("alert-1") }
    assertEquals(Route.SIGNIN, currentRoute())

    update { destination = StartDestination.PERSONAL_INFO }
    assertEquals(Route.PERSONAL_INFO, currentRoute())
    assertEquals(0, opened)

    update { destination = StartDestination.HOME }
    assertEquals(Route.MY_ALERT, currentRoute())
    assertEquals(1, opened)

    update { navController.popBackStack() }
    assertEquals(Route.HOME, currentRoute())
  }

  @Test
  fun whenAlreadyOnHome_opensTheRouteOnce_andNothingWithoutARoute() {
    update { destination = StartDestination.HOME }
    assertEquals(Route.HOME, currentRoute())
    assertEquals(0, opened)

    update { route = Route.myAlert("alert-1") }
    assertEquals(Route.MY_ALERT, currentRoute())
    assertEquals(1, opened)
  }

  @Test
  fun anUnknownRoute_isIgnoredWithoutCrashing() {
    update { destination = StartDestination.HOME }

    update { route = "not_a_route" }

    assertEquals(Route.HOME, currentRoute())
    assertEquals(1, opened)
  }

  @Test
  fun notificationRouteOf_acceptsOnlyAlertRoutes_andIgnoresRelaunchesFromRecents() {
    fun intentWith(route: String) = Intent().putExtra(EXTRA_ROUTE, route)

    assertEquals(Route.myAlert("a"), notificationRouteOf(intentWith(Route.myAlert("a"))))
    assertEquals(Route.alertDetail("a"), notificationRouteOf(intentWith(Route.alertDetail("a"))))

    assertNull("no notification", notificationRouteOf(Intent()))
    assertNull("another screen", notificationRouteOf(intentWith(Route.EDIT_PROFILE)))
    val relaunch =
        intentWith(Route.myAlert("a")).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
    assertNull("relaunch from Recents", notificationRouteOf(relaunch))
  }
}
