// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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

/**
 * The user's missing pets on Home: the missing pet's own card in "Your pets" is red, has a
 * "Missing" chip and opens its alert; the other pets are "Safe" and open their profile.
 */
@RunWith(AndroidJUnit4::class)
class HomeMissingPetsTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val alertsOpened = mutableListOf<String>()
  private val profilesOpened = mutableListOf<String>()

  private fun show(state: HomeUiState) {
    composeTestRule.setContent {
      HomeScreenContent(
          state = state,
          onMissingPetClick = { alertsOpened += it },
          onPetClick = { profilesOpened += it },
      )
    }
  }

  @Test
  fun noOpenAlert_noPetIsMissing() {
    show(HomeUiState(pets = HomeSection(items = listOf(LUNA, MILO))))

    composeTestRule.onNodeWithTag(HomeScreenTestTags.petCard(LUNA.id)).assertExists()
    composeTestRule.onNodeWithTag(HomeScreenTestTags.petCard(MILO.id)).assertExists()
    composeTestRule.onAllNodesWithText("Safe", useUnmergedTree = true).assertCountEquals(2)
    // The chips are inside the clickable pet cards, whose semantics merge their children
    composeTestRule
        .onNodeWithTag(HomeScreenTestTags.missingBadge(MILO.id), useUnmergedTree = true)
        .assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(HomeScreenTestTags.missingBadge(LUNA.id), useUnmergedTree = true)
        .assertDoesNotExist()
  }

  @Test
  fun onlyTheMissingPet_isMarkedMissing() {
    show(
        HomeUiState(
            pets = HomeSection(items = listOf(MILO, LUNA)),
            ownLostPets = listOf(MILO_ALERT),
            nowMillis = NOW + 2 * HOUR,
        )
    )

    composeTestRule
        .onNodeWithTag(HomeScreenTestTags.missingBadge(MILO.id), useUnmergedTree = true)
        .assertExists()
    composeTestRule
        .onNodeWithTag(HomeScreenTestTags.missingBadge(LUNA.id), useUnmergedTree = true)
        .assertDoesNotExist()
    composeTestRule.onNodeWithText("Lost 2 h ago", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Safe", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun tappingTheMissingPet_opensItsAlert() {
    show(
        HomeUiState(
            pets = HomeSection(items = listOf(MILO, LUNA)),
            ownLostPets = listOf(MILO_ALERT),
        )
    )

    composeTestRule.onNodeWithTag(HomeScreenTestTags.petCard(MILO.id)).performClick()

    assertEquals(listOf(MILO_ALERT.id), alertsOpened)
    assertEquals(emptyList<String>(), profilesOpened)
  }

  @Test
  fun tappingAnotherPet_opensItsProfile() {
    show(
        HomeUiState(
            pets = HomeSection(items = listOf(MILO, LUNA)),
            ownLostPets = listOf(MILO_ALERT),
        )
    )

    composeTestRule.onNodeWithTag(HomeScreenTestTags.petCard(LUNA.id)).performClick()

    assertEquals(listOf(LUNA.id), profilesOpened)
    assertEquals(emptyList<String>(), alertsOpened)
  }

  @Test
  fun alertOfAPetNotInTheList_stillShowsItsCard_whichOpensTheAlert() {
    // Nala's alert is open, but her profile is not loaded: her card is drawn from the alert
    show(HomeUiState(pets = HomeSection(items = listOf(LUNA)), ownLostPets = listOf(NALA_ALERT)))

    composeTestRule
        .onNodeWithTag(HomeScreenTestTags.missingBadge(NALA_ALERT.petId), useUnmergedTree = true)
        .assertExists()
    composeTestRule.onNodeWithTag(HomeScreenTestTags.petCard(NALA_ALERT.petId)).performClick()

    assertEquals(listOf(NALA_ALERT.id), alertsOpened)
    assertEquals(emptyList<String>(), profilesOpened)
  }

  @Test
  fun petsFailedToLoad_theMissingPetCanStillBeReached() {
    show(
        HomeUiState(
            pets = HomeSection(errorMessage = "Could not load your pets"),
            ownLostPets = listOf(MILO_ALERT),
        )
    )

    composeTestRule.onNodeWithTag(HomeScreenTestTags.PETS_ERROR).assertExists()
    composeTestRule.onNodeWithTag(HomeScreenTestTags.petCard(MILO.id)).performClick()

    assertEquals(listOf(MILO_ALERT.id), alertsOpened)
  }

  @Test
  fun lostPetsNotListed_keepsOnlyAlertsWhosePetIsNotLoaded() {
    val state =
        HomeUiState(
            pets = HomeSection(items = listOf(MILO, LUNA)),
            ownLostPets = listOf(MILO_ALERT, NALA_ALERT),
        )

    assertEquals(listOf(NALA_ALERT), state.lostPetsNotListed)
    assertEquals(mapOf(MILO.id to MILO_ALERT, "nala" to NALA_ALERT), state.alertByPetId)
  }

  @Test
  fun missingDetail_showsTheTimeOnlyWhenItIsKnown() {
    assertEquals("Missing", missingDetail(lostAtMillis = NOW, nowMillis = 0L))
    assertEquals("Lost 2 h ago", missingDetail(lostAtMillis = NOW - 2 * HOUR, nowMillis = NOW))
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
