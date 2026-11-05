// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The user's missing pets on Home: one banner per open alert, and a "Missing" badge on the pet. */
@RunWith(AndroidJUnit4::class)
class HomeMissingPetsTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val clicked = mutableListOf<String>()

  private fun show(state: HomeUiState) {
    composeTestRule.setContent {
      HomeScreenContent(state = state, onMissingPetClick = { clicked += it })
    }
  }

  @Test
  fun noOpenAlert_showsNoBanner() {
    show(HomeUiState(pets = HomeSection(items = listOf(LUNA, MILO))))

    composeTestRule
        .onNodeWithTag(HomeScreenTestTags.missingBanner(MILO_ALERT.id))
        .assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(HomeScreenTestTags.missingBadge(MILO.id), useUnmergedTree = true)
        .assertDoesNotExist()
  }

  @Test
  fun oneBannerPerMissingPet() {
    show(HomeUiState(ownLostPets = listOf(MILO_ALERT, NALA_ALERT)))

    composeTestRule
        .onNodeWithTag(HomeScreenTestTags.missingBanner(MILO_ALERT.id))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(HomeScreenTestTags.missingBanner(NALA_ALERT.id))
        .assertIsDisplayed()
    composeTestRule.onNodeWithText("Milo is missing").assertIsDisplayed()
  }

  @Test
  fun tappingABanner_opensThatAlert() {
    show(HomeUiState(ownLostPets = listOf(MILO_ALERT, NALA_ALERT)))

    composeTestRule.onNodeWithTag(HomeScreenTestTags.missingBanner(NALA_ALERT.id)).performClick()

    assertEquals(listOf(NALA_ALERT.id), clicked)
  }

  @Test
  fun missingPet_hasABadge_theOthersDoNot() {
    show(
        HomeUiState(
            pets = HomeSection(items = listOf(MILO, LUNA)),
            ownLostPets = listOf(MILO_ALERT),
        )
    )

    // The badge is inside the clickable pet card, whose semantics merge its children
    composeTestRule
        .onNodeWithTag(HomeScreenTestTags.missingBadge(MILO.id), useUnmergedTree = true)
        .assertExists()
    composeTestRule
        .onNodeWithTag(HomeScreenTestTags.missingBadge(LUNA.id), useUnmergedTree = true)
        .assertDoesNotExist()
  }

  @Test
  fun missingDetail_showsTheTimeOnlyWhenItIsKnown() {
    assertEquals("See the sightings", missingDetail(lostAtMillis = NOW, nowMillis = 0L))
    assertEquals(
        "Lost 2 h ago · see the sightings",
        missingDetail(lostAtMillis = NOW - 2 * HOUR, nowMillis = NOW),
    )
  }

  private companion object {
    const val NOW = 1_700_000_000_000L
    const val HOUR = 3_600_000L
    val MILO = Pet(id = "milo", ownerId = "me", name = "Milo", species = Species.CAT)
    val LUNA = Pet(id = "luna", ownerId = "me", name = "Luna", species = Species.CAT)
    val MILO_ALERT =
        Alert(
            id = "alert-milo",
            petId = MILO.id,
            ownerId = "me",
            lastKnownLocation = Location(46.2, 6.1),
            lostAtMillis = NOW,
            petName = "Milo",
            petSpecies = Species.CAT,
        )
    val NALA_ALERT =
        MILO_ALERT.copy(
            id = "alert-nala",
            petId = "nala",
            petName = "Nala",
            petSpecies = Species.DOG,
        )
  }
}
