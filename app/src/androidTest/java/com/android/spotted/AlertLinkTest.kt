// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.ui.StartDestination
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A `spotted://alert/<id>` link opens that alert's detail: Android sends it to [MainActivity], and
 * [routeOf] turns it into the route that [NotificationRouting] opens above Home, after sign-in.
 */
@RunWith(AndroidJUnit4::class)
class AlertLinkTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun linkIntent(link: String) = Intent(Intent.ACTION_VIEW, Uri.parse(link))

  @Test
  fun theLink_isHandledByMainActivity() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val intent = linkIntent("spotted://alert/a1").setPackage(context.packageName)

    val activity = context.packageManager.resolveActivity(intent, 0)?.activityInfo

    assertEquals(MainActivity::class.java.name, activity?.name)
  }

  @Test
  fun aSignedOutUser_signsInFirst_thenSeesTheAlertOfTheLink() {
    lateinit var navController: NavHostController
    var destination by mutableStateOf(StartDestination.SIGNIN)
    var route by mutableStateOf(routeOf(linkIntent("spotted://alert/a1")))
    composeTestRule.setContent {
      navController = rememberNavController()
      StartRouting(destination, navController)
      NotificationRouting(route, destination, navController) { route = null }
      NavHost(navController = navController, startDestination = Route.SIGNIN) {
        composable(Route.SIGNIN) { Text("sign in") }
        composable(Route.HOME) { Text("home") }
        composable(Route.ALERT_DETAIL) { entry ->
          Text("alert ${entry.arguments?.getString(Route.ALERT_ID)}")
        }
      }
    }
    composeTestRule.onNodeWithText("sign in").assertIsDisplayed()

    composeTestRule.runOnIdle { destination = StartDestination.HOME }

    composeTestRule.onNodeWithText("alert a1").assertIsDisplayed()
    composeTestRule.runOnIdle { navController.popBackStack() }
    composeTestRule.onNodeWithText("home").assertIsDisplayed()
  }
}
