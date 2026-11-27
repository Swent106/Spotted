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

/**
 * UI tests for the "Lost near you" section of [HomeScreenContent] (the alert carousel), with
 * hand-built states.
 */
class LostPetsSectionTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun setContent(
      state: HomeUiState,
      onAlertClick: (String) -> Unit = {},
      onRetryClick: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      HomeScreenContent(
          state = state,
          onAlertClick = onAlertClick,
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

  /** Scrolls Home to the carousel, then the carousel until the card of [alertId] is composed. */
  private fun scrollToCard(alertId: String): SemanticsNodeInteraction {
    val card = HomeScreenTestTags.alertCard(alertId)
    scrollTo(HomeScreenTestTags.ALERT_CAROUSEL).performScrollToNode(hasTestTag(card))
    return node(card)
  }

  @Test
  fun lostPetsAreListed_withNameSpeciesAndTimeLost() {
    setContent(HomeUiState(carouselAlerts = HomeSection(listOf(MILO, NALA)), nowMillis = NOW))

    scrollToCard(MILO.id).assertIsDisplayed()
    scrollToCard(NALA.id).assertIsDisplayed()
    composeTestRule.onNodeWithText("Cat").assertExists()
    composeTestRule.onNodeWithText("Lost 2 h ago").assertExists()
    composeTestRule.onNodeWithText("Lost yesterday").assertExists()
    node(HomeScreenTestTags.ALERTS_TITLE).assertTextEquals("2 pets missing near you")
    node(HomeScreenTestTags.ALERTS_EMPTY).assertDoesNotExist()
  }

  @Test
  fun refreshingLostPets_keepsTheListAndShowsNoLoader() {
    setContent(HomeUiState(carouselAlerts = HomeSection(listOf(MILO), isLoading = true)))

    scrollToCard(MILO.id).assertIsDisplayed()
    node(HomeScreenTestTags.ALERTS_LOADER).assertDoesNotExist()
    node(HomeScreenTestTags.ALERTS_EMPTY).assertDoesNotExist()
  }

  @Test
  fun lostPetsErrorAfterARefresh_keepsTheListAndShowsTheErrorBelow() {
    setContent(HomeUiState(carouselAlerts = HomeSection(listOf(MILO), errorMessage = ALERTS_ERROR)))

    scrollToCard(MILO.id).assertIsDisplayed()
    scrollTo(HomeScreenTestTags.ALERTS_ERROR).assertTextEquals(ALERTS_ERROR)
    node(HomeScreenTestTags.ALERTS_EMPTY).assertDoesNotExist()
  }

  @Test
  fun oneLostPet_isCountedInTheSingular() {
    setContent(HomeUiState(carouselAlerts = HomeSection(listOf(MILO))))

    node(HomeScreenTestTags.ALERTS_TITLE).assertTextEquals("1 pet missing near you")
  }

  @Test
  fun noLostPets_showsTheirEmptyState() {
    setContent(HomeUiState(carouselAlerts = HomeSection()))

    scrollTo(HomeScreenTestTags.ALERTS_EMPTY).assertTextEquals(HomeScreenTexts.NO_LOST_PETS)
    node(HomeScreenTestTags.ALERTS_TITLE).assertTextEquals("0 pets missing near you")
  }

  @Test
  fun lostPetsError_showsTheirError_andNoEmptyState() {
    setContent(HomeUiState(carouselAlerts = HomeSection(errorMessage = ALERTS_ERROR)))

    scrollTo(HomeScreenTestTags.ALERTS_ERROR).assertTextEquals(ALERTS_ERROR)
    node(HomeScreenTestTags.ALERTS_EMPTY).assertDoesNotExist()
  }

  @Test
  fun lostPetsRetry_callsItsCallback() {
    var retries = 0
    setContent(
        HomeUiState(carouselAlerts = HomeSection(errorMessage = ALERTS_ERROR)),
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
    setContent(HomeUiState(pets = HomeSection(), carouselAlerts = HomeSection()))

    scrollTo(HomeScreenTestTags.PETS_EMPTY).assertIsDisplayed()
    scrollTo(HomeScreenTestTags.ALERTS_EMPTY).assertIsDisplayed()
  }

  @Test
  fun alertsFailing_stillShowsThePets() {
    setContent(
        HomeUiState(
            pets = HomeSection(listOf(LUNA)),
            carouselAlerts = HomeSection(errorMessage = ALERTS_ERROR),
        )
    )

    scrollTo(HomeScreenTestTags.petCard(LUNA.id)).assertIsDisplayed()
    scrollTo(HomeScreenTestTags.ALERTS_ERROR).assertIsDisplayed()
    node(HomeScreenTestTags.PETS_ERROR).assertDoesNotExist()
  }

  @Test
  fun tappingACard_passesItsAlertId() {
    val clicked = mutableListOf<String>()
    setContent(
        HomeUiState(carouselAlerts = HomeSection(listOf(MILO, NALA))),
        onAlertClick = { clicked += it },
    )

    scrollToCard(NALA.id).performClick()

    assertEquals(listOf(NALA.id), clicked)
  }

  @Test
  fun carousel_scrollsHorizontally_toTheLastCard() {
    val alerts = (1..6).map { MILO.copy(id = "alert-$it", petName = "Pet $it") }
    setContent(HomeUiState(carouselAlerts = HomeSection(alerts)))

    scrollTo(HomeScreenTestTags.ALERT_CAROUSEL)
    // Cards are 220 dp wide: the last one starts off screen, to the right
    node(HomeScreenTestTags.alertCard("alert-6")).assertDoesNotExist()
    val rowTop = node(HomeScreenTestTags.alertCard("alert-1")).fetchSemanticsNode().boundsInRoot.top

    scrollToCard("alert-6").assertIsDisplayed()

    // Same row as the first card: the carousel scrolled sideways, not the page
    val lastTop =
        node(HomeScreenTestTags.alertCard("alert-6")).fetchSemanticsNode().boundsInRoot.top
    assertEquals(rowTop, lastTop, 1f)
    node(HomeScreenTestTags.ALERTS_TITLE).assertTextEquals("6 pets missing near you")
  }

  @Test
  fun loadingWithoutCards_showsTheLoaderAndNoCarousel() {
    setContent(HomeUiState(carouselAlerts = HomeSection(isLoading = true)))

    scrollTo(HomeScreenTestTags.ALERTS_LOADER).assertIsDisplayed()
    node(HomeScreenTestTags.ALERT_CAROUSEL).assertDoesNotExist()
    node(HomeScreenTestTags.ALERTS_EMPTY).assertDoesNotExist()
  }

  @Test
  fun noLostPets_showsNoCarousel() {
    setContent(HomeUiState(carouselAlerts = HomeSection()))

    scrollTo(HomeScreenTestTags.ALERTS_EMPTY).assertIsDisplayed()
    node(HomeScreenTestTags.ALERT_CAROUSEL).assertDoesNotExist()
  }

  @Test
  fun lostPetDistance_isShownOnlyWhenKnown() {
    setContent(
        HomeUiState(
            carouselAlerts = HomeSection(listOf(MILO, NALA)),
            distanceTexts = mapOf(MILO.id to "600 m away"),
        )
    )

    // The clickable card merges its texts: scroll to the card, then look inside it
    scrollToCard(MILO.id)
    node(HomeScreenTestTags.alertDistance(MILO.id)).assertTextEquals("600 m away")
    scrollToCard(NALA.id).assertIsDisplayed()
    node(HomeScreenTestTags.alertDistance(NALA.id)).assertDoesNotExist()
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
