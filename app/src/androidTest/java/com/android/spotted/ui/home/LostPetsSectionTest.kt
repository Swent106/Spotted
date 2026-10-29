// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.home

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
      onRetryClick: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      HomeScreenContent(
          state = state,
          onAlertClick = onAlertClick,
          onSeenPetClick = onSeenPetClick,
          onRetryClick = onRetryClick,
      )
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  /** Scrolls the lazy Home list until the node tagged [tag] is composed, then returns it. */
  private fun scrollTo(tag: String): SemanticsNodeInteraction {
    composeTestRule.onNodeWithTag(HomeScreenTestTags.BODY).performScrollToNode(hasTestTag(tag))
    return node(tag)
  }

  @Test
  fun lostPetsAreListed_withNameSpeciesAndTimeLost() {
    setContent(HomeUiState(lostPetsNearby = HomeSection(listOf(MILO, NALA)), nowMillis = NOW))

    scrollTo(HomeScreenTestTags.alertCard(MILO.id)).assertIsDisplayed()
    scrollTo(HomeScreenTestTags.alertCard(NALA.id)).assertIsDisplayed()
    composeTestRule.onNodeWithText("Cat").assertExists()
    composeTestRule.onNodeWithText("Lost 2 h ago").assertExists()
    composeTestRule.onNodeWithText("Lost yesterday").assertExists()
    node(HomeScreenTestTags.ALERTS_TITLE).assertTextEquals("2 pets missing near you")
    node(HomeScreenTestTags.ALERTS_EMPTY).assertDoesNotExist()
  }

  @Test
  fun onlyTheNewestLostPet_hasTheSeenButton() {
    setContent(HomeUiState(lostPetsNearby = HomeSection(listOf(MILO, NALA))))

    scrollTo(HomeScreenTestTags.seenButton(MILO.id)).assertExists()
    // Nala's card is composed before checking that it has no button
    scrollTo(HomeScreenTestTags.alertCard(NALA.id)).assertExists()
    node(HomeScreenTestTags.seenButton(NALA.id)).assertDoesNotExist()
  }

  @Test
  fun firstLostPetOfTheState_isFeatured() {
    // The ViewModel sorts the alerts newest first: the screen keeps the order it is given
    setContent(HomeUiState(lostPetsNearby = HomeSection(listOf(NALA, MILO))))

    scrollTo(HomeScreenTestTags.seenButton(NALA.id)).assertExists()
    scrollTo(HomeScreenTestTags.alertCard(MILO.id)).assertExists()
    node(HomeScreenTestTags.seenButton(MILO.id)).assertDoesNotExist()
  }

  @Test
  fun refreshingLostPets_keepsTheListAndShowsNoLoader() {
    setContent(HomeUiState(lostPetsNearby = HomeSection(listOf(MILO), isLoading = true)))

    scrollTo(HomeScreenTestTags.alertCard(MILO.id)).assertIsDisplayed()
    node(HomeScreenTestTags.ALERTS_LOADER).assertDoesNotExist()
    node(HomeScreenTestTags.ALERTS_EMPTY).assertDoesNotExist()
  }

  @Test
  fun lostPetsErrorAfterARefresh_keepsTheListAndShowsTheErrorBelow() {
    setContent(HomeUiState(lostPetsNearby = HomeSection(listOf(MILO), errorMessage = ALERTS_ERROR)))

    scrollTo(HomeScreenTestTags.alertCard(MILO.id)).assertIsDisplayed()
    scrollTo(HomeScreenTestTags.ALERTS_ERROR).assertTextEquals(ALERTS_ERROR)
    node(HomeScreenTestTags.ALERTS_EMPTY).assertDoesNotExist()
  }

  @Test
  fun oneLostPet_isCountedInTheSingular() {
    setContent(HomeUiState(lostPetsNearby = HomeSection(listOf(MILO))))

    node(HomeScreenTestTags.ALERTS_TITLE).assertTextEquals("1 pet missing near you")
  }

  @Test
  fun noLostPets_showsTheirEmptyState() {
    setContent(HomeUiState(lostPetsNearby = HomeSection()))

    scrollTo(HomeScreenTestTags.ALERTS_EMPTY).assertTextEquals(HomeScreenTexts.NO_LOST_PETS)
    node(HomeScreenTestTags.ALERTS_TITLE).assertTextEquals("0 pets missing near you")
  }

  @Test
  fun lostPetsError_showsTheirError_andNoEmptyState() {
    setContent(HomeUiState(lostPetsNearby = HomeSection(errorMessage = ALERTS_ERROR)))

    scrollTo(HomeScreenTestTags.ALERTS_ERROR).assertTextEquals(ALERTS_ERROR)
    node(HomeScreenTestTags.ALERTS_EMPTY).assertDoesNotExist()
  }

  @Test
  fun lostPetsRetry_callsItsCallback() {
    var retries = 0
    setContent(
        HomeUiState(lostPetsNearby = HomeSection(errorMessage = ALERTS_ERROR)),
        onRetryClick = { retries++ },
    )

    scrollTo(HomeScreenTestTags.ALERTS_RETRY).performClick()

    assertEquals(1, retries)
  }

  @Test
  fun areaLabel_showsTheHomeArea() {
    setContent(HomeUiState(homeArea = "Plainpalais"))

    composeTestRule.onNodeWithText("PLAINPALAIS · 3 KM AROUND YOU").assertExists()
  }

  @Test
  fun bothSections_showTheirOwnEmptyState() {
    setContent(HomeUiState(pets = HomeSection(), lostPetsNearby = HomeSection()))

    scrollTo(HomeScreenTestTags.PETS_EMPTY).assertIsDisplayed()
    scrollTo(HomeScreenTestTags.ALERTS_EMPTY).assertIsDisplayed()
  }

  @Test
  fun alertsFailing_stillShowsThePets() {
    setContent(
        HomeUiState(
            pets = HomeSection(listOf(LUNA)),
            lostPetsNearby = HomeSection(errorMessage = ALERTS_ERROR),
        )
    )

    scrollTo(HomeScreenTestTags.petCard(LUNA.id)).assertIsDisplayed()
    scrollTo(HomeScreenTestTags.ALERTS_ERROR).assertIsDisplayed()
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

    scrollTo(HomeScreenTestTags.alertCard(NALA.id)).performClick()
    scrollTo(HomeScreenTestTags.seenButton(MILO.id)).performClick()

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
