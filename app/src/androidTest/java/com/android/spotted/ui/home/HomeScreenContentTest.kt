package com.android.spotted.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
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

  private fun setContent(state: HomeUiState, onReportMissingClick: () -> Unit = {}) {
    composeTestRule.setContent {
      HomeScreenContent(state = state, onReportMissingClick = onReportMissingClick)
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  @Test
  fun allPetsAreListed() {
    setContent(HomeUiState(pets = HomeSection(listOf(LUNA, REX))))

    node(HomeScreenTestTags.petCard(LUNA.id)).performScrollTo().assertIsDisplayed()
    node(HomeScreenTestTags.petCard(REX.id)).performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Rex").assertExists()
    node(HomeScreenTestTags.PETS_EMPTY).assertDoesNotExist()
  }

  @Test
  fun noPets_showsTheEmptyState() {
    setContent(HomeUiState(pets = HomeSection()))

    node(HomeScreenTestTags.PETS_EMPTY).performScrollTo().assertTextEquals(HomeScreenTexts.NO_PETS)
  }

  @Test
  fun petsLoading_showsTheLoader_andNoEmptyState() {
    setContent(HomeUiState(pets = HomeSection(isLoading = true)))

    node(HomeScreenTestTags.PETS_LOADER).assertExists()
    node(HomeScreenTestTags.PETS_EMPTY).assertDoesNotExist()
  }

  @Test
  fun petsError_showsTheError_andNoEmptyState() {
    setContent(HomeUiState(pets = HomeSection(errorMessage = PETS_ERROR)))

    node(HomeScreenTestTags.PETS_ERROR).performScrollTo().assertTextEquals(PETS_ERROR)
    node(HomeScreenTestTags.PETS_EMPTY).assertDoesNotExist()
  }

  @Test
  fun header_greetsTheUser_andScreenErrorIsShown() {
    setContent(HomeUiState(userName = "Rayen", errorMessage = HomeViewModel.NOT_SIGNED_IN))

    composeTestRule.onNodeWithText("Hey Rayen").assertIsDisplayed()
    node(HomeScreenTestTags.ERROR_MESSAGE).assertTextEquals(HomeViewModel.NOT_SIGNED_IN)
    node(HomeScreenTestTags.TAB_BAR).assertIsDisplayed()
  }

  @Test
  fun reportMissingButton_callsItsCallback() {
    var clicks = 0
    setContent(HomeUiState(), onReportMissingClick = { clicks++ })

    node(HomeScreenTestTags.REPORT_MISSING_BUTTON).performScrollTo().performClick()

    assertEquals(1, clicks)
  }

  private companion object {
    const val PETS_ERROR = "Couldn't load your pets. Please try again."
    val LUNA = Pet(id = "luna", ownerId = "me", name = "Luna", species = Species.DOG)
    val REX = Pet(id = "rex", ownerId = "me", name = "Rex", species = Species.DOG)
  }
}
