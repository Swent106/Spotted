package com.android.spotted.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** UI tests for the "Lost near you" section of [HomeScreenContent], with hand-built states. */
class LostPetsSectionTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun setContent(
      state: HomeUiState,
      onAlertClick: (String) -> Unit = {},
      onSeenPetClick: (String) -> Unit = {},
  ) {
    composeTestRule.setContent {
      HomeScreenContent(state = state, onAlertClick = onAlertClick, onSeenPetClick = onSeenPetClick)
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  @Test
  fun lostPetsAreListed_withNameSpeciesAndTimeLost() {
    setContent(HomeUiState(lostPetsNearby = HomeSection(listOf(MILO, NALA)), nowMillis = NOW))

    node(HomeScreenTestTags.alertCard(MILO.id)).performScrollTo().assertIsDisplayed()
    node(HomeScreenTestTags.alertCard(NALA.id)).performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Cat").assertExists()
    composeTestRule.onNodeWithText("Lost 2 h ago").assertExists()
    composeTestRule.onNodeWithText("Lost yesterday").assertExists()
    node(HomeScreenTestTags.ALERTS_TITLE).assertTextEquals("2 pets missing near you")
    node(HomeScreenTestTags.ALERTS_EMPTY).assertDoesNotExist()
  }

  @Test
  fun onlyTheNewestLostPet_hasTheSeenButton() {
    setContent(HomeUiState(lostPetsNearby = HomeSection(listOf(MILO, NALA))))

    node(HomeScreenTestTags.seenButton(MILO.id)).assertExists()
    node(HomeScreenTestTags.seenButton(NALA.id)).assertDoesNotExist()
  }

  @Test
  fun noLostPets_showsTheirEmptyState() {
    setContent(HomeUiState(lostPetsNearby = HomeSection()))

    node(HomeScreenTestTags.ALERTS_EMPTY)
        .performScrollTo()
        .assertTextEquals(HomeScreenTexts.NO_LOST_PETS)
    node(HomeScreenTestTags.ALERTS_TITLE).assertTextEquals("0 pets missing near you")
  }

  @Test
  fun lostPetsError_showsTheirError_andNoEmptyState() {
    setContent(HomeUiState(lostPetsNearby = HomeSection(errorMessage = ALERTS_ERROR)))

    node(HomeScreenTestTags.ALERTS_ERROR).performScrollTo().assertTextEquals(ALERTS_ERROR)
    node(HomeScreenTestTags.ALERTS_EMPTY).assertDoesNotExist()
  }

  @Test
  fun areaLabel_showsTheHomeArea() {
    setContent(HomeUiState(homeArea = "Plainpalais"))

    composeTestRule.onNodeWithText("PLAINPALAIS · 3 KM AROUND YOU").assertExists()
  }

  @Test
  fun bothSections_showTheirOwnEmptyState() {
    setContent(HomeUiState(pets = HomeSection(), lostPetsNearby = HomeSection()))

    node(HomeScreenTestTags.PETS_EMPTY).performScrollTo().assertIsDisplayed()
    node(HomeScreenTestTags.ALERTS_EMPTY).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun alertsFailing_stillShowsThePets() {
    setContent(
        HomeUiState(
            pets = HomeSection(listOf(LUNA)),
            lostPetsNearby = HomeSection(errorMessage = ALERTS_ERROR),
        )
    )

    node(HomeScreenTestTags.petCard(LUNA.id)).performScrollTo().assertIsDisplayed()
    node(HomeScreenTestTags.ALERTS_ERROR).performScrollTo().assertIsDisplayed()
    node(HomeScreenTestTags.PETS_ERROR).assertDoesNotExist()
  }

  @Test
  fun alertCard_andSeenButton_passTheAlertId() {
    val clicked = mutableListOf<String>()
    val seen = mutableListOf<String>()
    setContent(
        HomeUiState(lostPetsNearby = HomeSection(listOf(MILO, NALA))),
        onAlertClick = { clicked += it },
        onSeenPetClick = { seen += it },
    )

    node(HomeScreenTestTags.alertCard(NALA.id)).performScrollTo().performClick()
    node(HomeScreenTestTags.seenButton(MILO.id)).performScrollTo().performClick()

    assertEquals(listOf(NALA.id), clicked)
    assertEquals(listOf(MILO.id), seen)
  }

  private companion object {
    const val NOW = 1_000_000_000_000L
    const val HOUR = 3_600_000L
    const val ALERTS_ERROR = "Couldn't load the lost pets near you. Please try again."

    val LUNA = Pet(id = "luna", ownerId = "me", name = "Luna", species = Species.DOG)
    val MILO =
        Alert(
            id = "milo",
            petId = "milo",
            ownerId = "someone",
            lastKnownLocation = Location(latitude = 46.2, longitude = 6.14),
            lostAtMillis = NOW - 2 * HOUR,
            petName = "Milo",
            petSpecies = Species.CAT,
        )
    val NALA =
        MILO.copy(
            id = "nala",
            petId = "nala",
            petName = "Nala",
            petSpecies = Species.DOG,
            lostAtMillis = NOW - 30 * HOUR,
        )
  }
}
