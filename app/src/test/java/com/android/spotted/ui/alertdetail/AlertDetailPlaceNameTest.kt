// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.alertdetail

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Alert detail names the place the pet was last seen, and shows the coordinates until then. */
@RunWith(AndroidJUnit4::class)
class AlertDetailPlaceNameTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val alert =
      Alert(
          id = "alert-1",
          petId = "pet-1",
          ownerId = "owner-1",
          lastKnownLocation = Location(46.5070, 6.6270),
          lostAtMillis = 0L,
          petName = "Milo",
          petSpecies = Species.CAT,
      )

  private fun lostAtText() =
      composeTestRule
          .onNodeWithTag(AlertDetailTestTags.LOST_AT)
          .fetchSemanticsNode()
          .config[androidx.compose.ui.semantics.SemanticsProperties.Text]
          .joinToString()

  @Test
  fun withAPlaceName_showsItInsteadOfTheCoordinates() {
    composeTestRule.setContent {
      AlertDetailContent(AlertDetailUiState(alert = alert, placeName = "Ouchy, Lausanne"))
    }

    assertTrue(lostAtText(), lostAtText().endsWith("· Ouchy, Lausanne"))
    composeTestRule
        .onNodeWithTag(AlertDetailTestTags.PLACE, useUnmergedTree = true)
        .assertTextEquals("Around Ouchy, Lausanne")
    composeTestRule
        .onNodeWithTag(AlertDetailTestTags.LOCATION, useUnmergedTree = true)
        .assertTextEquals("46.5070, 6.6270")
  }

  @Test
  fun withoutAPlaceName_showsTheCoordinates() {
    composeTestRule.setContent { AlertDetailContent(AlertDetailUiState(alert = alert)) }

    assertTrue(lostAtText(), lostAtText().endsWith("· 46.5070, 6.6270"))
    composeTestRule
        .onNodeWithTag(AlertDetailTestTags.PLACE, useUnmergedTree = true)
        .assertTextEquals("Around last known location")
  }
}
