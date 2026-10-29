package com.android.spotted.ui.map

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlertsMapContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun noAlerts_showsTheMapAndTheEmptyMessage() {
    composeTestRule.setContent { AlertsMapContent(alerts = emptyList()) }

    composeTestRule.onNodeWithTag(AlertsMapTestTags.MAP).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AlertsMapTestTags.EMPTY).assertIsDisplayed()
  }

  @Test
  fun withAlerts_hidesTheEmptyMessage() {
    composeTestRule.setContent { AlertsMapContent(alerts = listOf(NALA)) }

    composeTestRule.onNodeWithTag(AlertsMapTestTags.MAP).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AlertsMapTestTags.EMPTY).assertDoesNotExist()
  }

  @Test
  fun backButton_callsOnBack() {
    var backClicks = 0
    composeTestRule.setContent { AlertsMapContent(alerts = emptyList(), onBack = { backClicks++ }) }

    composeTestRule.onNodeWithTag(AlertsMapTestTags.BACK).performClick()

    assertEquals(1, backClicks)
  }

  private companion object {
    val NALA =
        Alert(
            id = "nala",
            petId = "nala",
            ownerId = "someone",
            lastKnownLocation = Location(46.5197, 6.6323),
            lostAtMillis = 0L,
            petName = "Nala",
            petSpecies = Species.CAT,
        )
  }
}
