// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.map

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.Route
import com.android.spotted.homeDestinations
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

/**
 * On a device: Alert detail opened from the Map or Home goes back where it came from (#364), and
 * the Map keeps the view the user left it at.
 */
@RunWith(AndroidJUnit4::class)
class AlertDetailBackInstrumentedTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private lateinit var navController: NavHostController

  /** The app's real routes, with stand-ins for Home, the Map and Alert detail. */
  private fun setRoutes() {
    composeTestRule.setContent {
      navController = rememberNavController()
      NavHost(navController = navController, startDestination = Route.HOME) {
        homeDestinations(
            navController,
            homeContent = { navigation ->
              Column {
                Button(navigation.onMapClick, Modifier.testTag(OPEN_MAP)) { Text("Map") }
                Button({ navigation.onAlertClick(ALERT.id) }, Modifier.testTag(OPEN_ALERT)) {
                  Text("Alert")
                }
              }
            },
            mapContent = { _, onAlertClick ->
              Button({ onAlertClick(ALERT.id) }, Modifier.testTag(OPEN_ALERT)) { Text("Pin") }
            },
            alertDetailContent = { _, _, onBack ->
              Button(onBack, Modifier.testTag(BACK)) { Text("Back") }
            },
        )
      }
    }
    composeTestRule.waitForIdle()
  }

  private fun tap(tag: String) {
    composeTestRule.onNodeWithTag(tag).performClick()
    composeTestRule.waitForIdle()
  }

  private fun route() = navController.currentDestination?.route

  @Test
  fun fromTheMap_theBackButtonReturnsToTheMap() {
    setRoutes()
    tap(OPEN_MAP)
    tap(OPEN_ALERT)
    assertEquals(Route.ALERT_DETAIL, route())

    tap(BACK)

    assertEquals(Route.MAP, route())
  }

  @Test
  fun fromTheMap_systemBackReturnsToTheMap() {
    setRoutes()
    tap(OPEN_MAP)
    tap(OPEN_ALERT)

    composeTestRule.runOnUiThread {
      composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
    }
    composeTestRule.waitForIdle()

    assertEquals(Route.MAP, route())
  }

  @Test
  fun fromHome_theBackButtonReturnsHome() {
    setRoutes()
    tap(OPEN_ALERT)

    tap(BACK)

    assertEquals(Route.HOME, route())
  }

  @Test
  fun backFromAnAlert_keepsTheMapWhereTheUserLeftIt() {
    composeTestRule.setContent {
      navController = rememberNavController()
      NavHost(navController = navController, startDestination = Route.MAP) {
        composable(Route.MAP) {
          AlertsMapContent(listOf(ALERT), onAlertClick = { navController.navigate("alert") })
        }
        composable("alert") { Text("Alert") }
      }
    }
    composeTestRule.waitForIdle()
    // The user pans and zooms away from the alert
    composeTestRule.runOnUiThread {
      mapView().controller.setZoom(16.0)
      mapView().controller.setCenter(GeoPoint(46.3, 6.2))
    }

    composeTestRule.runOnUiThread { navController.navigate("alert") }
    composeTestRule.waitForIdle()
    composeTestRule.runOnUiThread { navController.popBackStack() }
    composeTestRule.waitForIdle()

    val map = mapView()
    assertEquals(46.3, map.mapCenter.latitude, 0.001)
    assertEquals(6.2, map.mapCenter.longitude, 0.001)
    assertEquals(16.0, map.zoomLevelDouble, 0.01)
  }

  /** The map currently on screen. */
  private fun mapView(): MapView =
      checkNotNull(findMap(composeTestRule.activity.window.decorView)) { "No map on screen" }

  private fun findMap(view: View): MapView? =
      view as? MapView
          ?: (view as? ViewGroup)?.let { group ->
            (0 until group.childCount).firstNotNullOfOrNull { findMap(group.getChildAt(it)) }
          }

  private companion object {
    const val OPEN_MAP = "test_openMap"
    const val OPEN_ALERT = "test_openAlert"
    const val BACK = "test_back"

    val ALERT =
        Alert(
            id = "alert-1",
            petId = "milo",
            ownerId = "owner",
            lastKnownLocation = Location(46.52, 6.63),
            lostAtMillis = 0L,
            petName = "Milo",
            petSpecies = Species.CAT,
        )
  }
}
