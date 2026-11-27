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
import com.android.spotted.model.alert.buildAlertLink
import com.android.spotted.ui.StartDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An alert link ([buildAlertLink]) opens that alert's detail: Android sends it to [MainActivity],
 * and [routeOf] turns it into the route that [NotificationRouting] opens above Home, after sign-in.
 */
@RunWith(AndroidJUnit4::class)
class AlertLinkTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun linkIntent(link: String) = Intent(Intent.ACTION_VIEW, Uri.parse(link))

  /** The activity of this app that the manifest gives [link] to, or null if none takes it. */
  private fun activityFor(link: String): String? {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val intent = linkIntent(link).setPackage(context.packageName)
    return context.packageManager.resolveActivity(intent, 0)?.activityInfo?.name
  }

  @Test
  fun theLink_isHandledByMainActivity() {
    assertEquals(MainActivity::class.java.name, activityFor(buildAlertLink("a1").getOrThrow()))
  }

  // The intent filter takes only alert links of our domain; everything else stays in the browser
  @Test
  fun anotherHostPathOrScheme_isNotHandledByTheApp() {
    assertNull(activityFor("https://example.com/alert/a1"))
    assertNull(activityFor("https://spotted-fd9bf.web.app/"))
    assertNull(activityFor("https://spotted-fd9bf.web.app/other/a1"))
    assertNull(activityFor("spotted://alert/a1"))
  }

  @Test
  fun aSignedOutUser_signsInFirst_thenSeesTheAlertOfTheLink() {
    lateinit var navController: NavHostController
    var destination by mutableStateOf(StartDestination.SIGNIN)
    var route by mutableStateOf(routeOf(linkIntent(buildAlertLink("a1").getOrThrow())))
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
