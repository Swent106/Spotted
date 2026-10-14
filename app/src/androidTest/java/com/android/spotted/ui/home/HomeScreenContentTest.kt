package com.android.spotted.ui.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.Species
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** UI tests for the stateless [HomeScreenContent], with hand-built states. */
class HomeScreenContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  /** Number of times each callback was called, by name. */
  private val clicks = mutableMapOf<String, Int>()

  private fun click(name: String): () -> Unit = { clicks[name] = (clicks[name] ?: 0) + 1 }

  private fun setContent(state: HomeUiState) {
    composeTestRule.setContent {
      HomeScreenContent(
          state = state,
          onReportMissingClick = click("report"),
          onRetryClick = click("retry"),
          onSettingsClick = click("settings"),
          onNotificationsClick = click("notifications"),
          onMapClick = click("map"),
          onCameraClick = click("camera"),
          onProfileClick = click("profile"),
      )
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  /** The text [name] inside the card of [pet]. */
  private fun nameInCard(name: String, pet: Pet) =
      composeTestRule.onNode(
          hasText(name) and hasAnyAncestor(hasTestTag(HomeScreenTestTags.petCard(pet.id))),
          useUnmergedTree = true,
      )

  @Test
  fun allPetsAreListed_eachInItsOwnCard_underOneTitle() {
    setContent(HomeUiState(pets = HomeSection(listOf(LUNA, REX))))

    node(HomeScreenTestTags.petCard(LUNA.id)).performScrollTo().assertIsDisplayed()
    node(HomeScreenTestTags.petCard(REX.id)).performScrollTo().assertIsDisplayed()
    nameInCard("Luna", LUNA).assertExists()
    nameInCard("Rex", REX).assertExists()
    composeTestRule.onAllNodesWithText(HomeScreenTexts.PETS_TITLE).assertCountEquals(1)
    node(HomeScreenTestTags.PETS_EMPTY).assertDoesNotExist()
  }

  @Test
  fun noPets_showsTheTitleAndTheEmptyState() {
    setContent(HomeUiState(pets = HomeSection()))

    node(HomeScreenTestTags.PETS_TITLE).assertTextEquals(HomeScreenTexts.PETS_TITLE)
    node(HomeScreenTestTags.PETS_EMPTY).performScrollTo().assertTextEquals(HomeScreenTexts.NO_PETS)
  }

  @Test
  fun petsLoading_showsTheLoader_andNoEmptyState() {
    setContent(HomeUiState(pets = HomeSection(isLoading = true)))

    node(HomeScreenTestTags.PETS_LOADER).assertExists()
    node(HomeScreenTestTags.PETS_EMPTY).assertDoesNotExist()
  }

  @Test
  fun refreshWithPetsShown_keepsThePets_andShowsNoLoader() {
    setContent(HomeUiState(pets = HomeSection(listOf(LUNA), isLoading = true)))

    node(HomeScreenTestTags.petCard(LUNA.id)).performScrollTo().assertIsDisplayed()
    node(HomeScreenTestTags.PETS_LOADER).assertDoesNotExist()
  }

  @Test
  fun petsError_showsTheError_andNoEmptyState() {
    setContent(HomeUiState(pets = HomeSection(errorMessage = PETS_ERROR)))

    node(HomeScreenTestTags.PETS_ERROR).performScrollTo().assertTextEquals(PETS_ERROR)
    node(HomeScreenTestTags.PETS_EMPTY).assertDoesNotExist()
  }

  @Test
  fun failedRefresh_keepsThePets_andShowsTheErrorUnderThem() {
    setContent(HomeUiState(pets = HomeSection(listOf(LUNA), errorMessage = PETS_ERROR)))

    node(HomeScreenTestTags.petCard(LUNA.id)).performScrollTo().assertIsDisplayed()
    node(HomeScreenTestTags.PETS_ERROR).performScrollTo().assertTextEquals(PETS_ERROR)
  }

  @Test
  fun retry_callsItsCallback() {
    setContent(HomeUiState(pets = HomeSection(errorMessage = PETS_ERROR)))

    node(HomeScreenTestTags.PETS_RETRY).performScrollTo().performClick()

    assertEquals(1, clicks["retry"])
  }

  @Test
  fun header_greetsTheUserByName() {
    setContent(HomeUiState(userName = "Rayen"))

    composeTestRule.onNodeWithText("Hey Rayen").assertIsDisplayed()
  }

  @Test
  fun header_withoutAName_saysHey() {
    setContent(HomeUiState(userName = ""))

    composeTestRule.onNodeWithText("Hey").assertIsDisplayed()
  }

  @Test
  fun screenError_isShown() {
    setContent(HomeUiState(errorMessage = NOT_SIGNED_IN))

    node(HomeScreenTestTags.ERROR_MESSAGE).assertTextEquals(NOT_SIGNED_IN)
  }

  @Test
  fun headerButtons_callTheirCallbacks() {
    setContent(HomeUiState())

    node(HomeScreenTestTags.SETTINGS_BUTTON).performClick()
    node(HomeScreenTestTags.NOTIFICATIONS_BUTTON).performClick()

    assertEquals(1, clicks["settings"])
    assertEquals(1, clicks["notifications"])
  }

  @Test
  fun tabs_callTheirCallbacks_andHomeIsSelected() {
    setContent(HomeUiState())

    node(HomeScreenTestTags.TAB_BAR).assertIsDisplayed()
    node(HomeScreenTestTags.TAB_HOME).assertIsSelected()
    node(HomeScreenTestTags.TAB_MAP).assertIsNotSelected().performClick()
    node(HomeScreenTestTags.TAB_CAMERA).performClick()
    node(HomeScreenTestTags.TAB_PROFILE).performClick()

    assertEquals(1, clicks["map"])
    assertEquals(1, clicks["camera"])
    assertEquals(1, clicks["profile"])
  }

  @Test
  fun reportMissingButton_callsItsCallback() {
    setContent(HomeUiState())

    node(HomeScreenTestTags.REPORT_MISSING_BUTTON).performScrollTo().performClick()

    assertEquals(1, clicks["report"])
  }

  private companion object {
    const val PETS_ERROR = "Couldn't load your pets. Please try again."
    const val NOT_SIGNED_IN = "You need to be signed in to see your pets."
    val LUNA = Pet(id = "luna", ownerId = "me", name = "Luna", species = Species.DOG)
    val REX = Pet(id = "rex", ownerId = "me", name = "Rex", species = Species.DOG)
  }
}
