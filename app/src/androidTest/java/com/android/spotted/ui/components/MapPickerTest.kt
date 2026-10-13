package com.android.spotted.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.android.spotted.model.Location
import org.junit.Rule
import org.junit.Test

class MapPickerTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun setContent(
      location: Location?,
      radiusKm: Int = 3,
      onLocationChange: (Location) -> Unit = {},
  ) {
    composeTestRule.setContent {
      MapPicker(
          location = location,
          radiusKm = radiusKm,
          onLocationChange = onLocationChange,
          modifier = Modifier.fillMaxWidth().height(200.dp),
      )
    }
  }

  @Test
  fun noLocation_showsMapAndHint_withoutRadiusLabel() {
    setContent(location = null)

    composeTestRule.onNodeWithTag(MapPickerTestTags.MAP).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(MapPickerTestTags.HINT, useUnmergedTree = true)
        .assertTextEquals("Tap the map to place a pin")
    composeTestRule
        .onNodeWithTag(MapPickerTestTags.RADIUS_LABEL, useUnmergedTree = true)
        .assertDoesNotExist()
  }

  @Test
  fun withLocation_showsRadiusLabel_insteadOfHint() {
    setContent(location = DefaultMapCenter, radiusKm = 5)

    composeTestRule
        .onNodeWithTag(MapPickerTestTags.RADIUS_LABEL, useUnmergedTree = true)
        .assertTextEquals("Within 5 km")
    composeTestRule
        .onNodeWithTag(MapPickerTestTags.HINT, useUnmergedTree = true)
        .assertDoesNotExist()
  }

  @Test
  fun customTexts_areShown() {
    composeTestRule.setContent {
      MapPicker(
          location = DefaultMapCenter,
          radiusKm = 10,
          onLocationChange = {},
          modifier = Modifier.fillMaxWidth().height(200.dp),
          radiusLabel = { "Alerts within $it km" },
      )
    }

    composeTestRule
        .onNodeWithTag(MapPickerTestTags.RADIUS_LABEL, useUnmergedTree = true)
        .assertTextEquals("Alerts within 10 km")
  }

  @Test
  fun tappingTheMap_reportsTheTappedLocation() {
    var tapped: Location? = null
    setContent(location = null, onLocationChange = { tapped = it })

    composeTestRule.onNodeWithTag(MapPickerTestTags.MAP).performClick()
    composeTestRule.waitUntil(timeoutMillis = 10_000) { tapped != null }
  }
}
