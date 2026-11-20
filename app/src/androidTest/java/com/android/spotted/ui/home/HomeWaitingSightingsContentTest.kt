// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Pet.Species
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests of the sightings waiting to be sent on Home (Figma 05b to 05e): the banner, the "Waiting
 * to send" sheet, deleting with a confirmation, and the "was sent" snackbar.
 */
@RunWith(AndroidJUnit4::class)
class HomeWaitingSightingsContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  private var sentShown = 0

  /**
   * Shows Home from [initial], with callbacks that change the state as the ViewModel would, so a
   * whole flow can be followed on screen.
   */
  private fun setContent(initial: HomeUiState) {
    composeTestRule.setContent {
      var state by remember { mutableStateOf(initial) }
      HomeScreenContent(
          state = state,
          onWaitingBannerClick = { state = state.copy(isWaitingSheetOpen = true) },
          onWaitingSheetClose = { state = state.copy(isWaitingSheetOpen = false) },
          onWaitingDeleteClick = { state = state.copy(waitingDeleteId = it) },
          onWaitingDeleteConfirm = {
            val left = state.waitingSightings.filterNot { it.id == state.waitingDeleteId }
            state =
                state.copy(
                    waitingSightings = left,
                    waitingDeleteId = null,
                    isWaitingSheetOpen = left.isNotEmpty(),
                )
          },
          onWaitingDeleteCancel = { state = state.copy(waitingDeleteId = null) },
          onSentSightingShown = {
            sentShown++
            state = state.copy(sentSighting = null)
          },
      )
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  private fun scrollToBanner() {
    composeTestRule
        .onNodeWithTag(HomeScreenTestTags.BODY)
        .performScrollToNode(hasTestTag(HomeScreenTestTags.WAITING_BANNER))
  }

  @Test
  fun banner_isHidden_whenNothingIsWaiting() {
    setContent(HomeUiState())

    node(HomeScreenTestTags.WAITING_BANNER).assertDoesNotExist()
  }

  @Test
  fun banner_showsTheNumberOfWaitingSightings() {
    setContent(HomeUiState(waitingSightings = listOf(MILO)))
    scrollToBanner()

    node(HomeScreenTestTags.WAITING_BANNER).assertIsDisplayed()
    composeTestRule.onNodeWithText("1 sighting waiting to send").assertIsDisplayed()
    composeTestRule.onNodeWithText("Sends automatically when you're back online").assertExists()
  }

  @Test
  fun banner_saysSightings_forMoreThanOne() {
    setContent(HomeUiState(waitingSightings = listOf(MILO, STRAY)))
    scrollToBanner()

    composeTestRule.onNodeWithText("2 sightings waiting to send").assertIsDisplayed()
  }

  @Test
  fun tappingTheBanner_opensTheWaitingList_andCloseClosesIt() {
    setContent(HomeUiState(waitingSightings = listOf(MILO, STRAY)))
    scrollToBanner()

    node(HomeScreenTestTags.WAITING_BANNER).performClick()

    node(HomeScreenTestTags.WAITING_SHEET).assertIsDisplayed()
    composeTestRule.onNodeWithText("Waiting to send").assertIsDisplayed()
    node(HomeScreenTestTags.waitingRow(MILO.id)).assertIsDisplayed()
    node(HomeScreenTestTags.waitingRow(STRAY.id)).assertIsDisplayed()
    composeTestRule.onNodeWithText("Milo").assertIsDisplayed()
    // Without an alert, the row shows the species
    composeTestRule.onNodeWithText("Dog").assertIsDisplayed()
    composeTestRule
        .onNodeWithText("Saved ${waitingTime(MILO.savedAtMillis)} · Plainpalais, Geneva")
        .assertIsDisplayed()

    node(HomeScreenTestTags.WAITING_CLOSE).performClick()
    composeTestRule.waitForIdle()

    node(HomeScreenTestTags.WAITING_SHEET).assertDoesNotExist()
  }

  @Test
  fun deleteCancelled_keepsTheSighting() {
    setContent(HomeUiState(waitingSightings = listOf(MILO), isWaitingSheetOpen = true))

    node(HomeScreenTestTags.waitingDelete(MILO.id)).performClick()
    node(HomeScreenTestTags.DELETE_DIALOG).assertIsDisplayed()
    composeTestRule.onNodeWithText("Delete this sighting?").assertIsDisplayed()
    composeTestRule
        .onNodeWithText("It won't be sent, and its photo is removed from your phone.")
        .assertIsDisplayed()
    node(HomeScreenTestTags.DELETE_CANCEL).performClick()

    node(HomeScreenTestTags.DELETE_DIALOG).assertDoesNotExist()
    node(HomeScreenTestTags.waitingRow(MILO.id)).assertIsDisplayed()
  }

  @Test
  fun deleteConfirmed_removesTheSighting() {
    setContent(HomeUiState(waitingSightings = listOf(MILO, STRAY), isWaitingSheetOpen = true))

    node(HomeScreenTestTags.waitingDelete(MILO.id)).performClick()
    node(HomeScreenTestTags.DELETE_CONFIRM).performClick()

    node(HomeScreenTestTags.DELETE_DIALOG).assertDoesNotExist()
    node(HomeScreenTestTags.waitingRow(MILO.id)).assertDoesNotExist()
    node(HomeScreenTestTags.waitingRow(STRAY.id)).assertIsDisplayed()
  }

  @Test
  fun deletingTheLastSighting_closesTheSheetAndHidesTheBanner() {
    setContent(HomeUiState(waitingSightings = listOf(MILO), isWaitingSheetOpen = true))

    node(HomeScreenTestTags.waitingDelete(MILO.id)).performClick()
    node(HomeScreenTestTags.DELETE_CONFIRM).performClick()
    composeTestRule.waitForIdle()

    node(HomeScreenTestTags.WAITING_SHEET).assertDoesNotExist()
    node(HomeScreenTestTags.WAITING_BANNER).assertDoesNotExist()
  }

  @Test
  fun sentSighting_showsTheSnackbarWithThePetName_thenIsMarkedShown() {
    setContent(HomeUiState(sentSighting = MILO))

    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      composeTestRule
          .onAllNodes(hasTestTag(HomeScreenTestTags.SENT_SNACKBAR), useUnmergedTree = true)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    composeTestRule.onNodeWithText("Your sighting of Milo was sent").assertIsDisplayed()

    composeTestRule.waitUntil(SNACKBAR_TIMEOUT_MILLIS) { sentShown == 1 }
    assertEquals(1, sentShown)
  }

  @Test
  fun sentSighting_withoutPetName_saysYourSightingWasSent() {
    setContent(HomeUiState(sentSighting = STRAY))

    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      composeTestRule
          .onAllNodes(hasTestTag(HomeScreenTestTags.SENT_SNACKBAR), useUnmergedTree = true)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    node(HomeScreenTestTags.SENT_SNACKBAR).assertIsDisplayed()
    composeTestRule.onNodeWithText("Your sighting was sent").assertIsDisplayed()
  }

  @Test
  fun otherSnackbars_keepTheirDefaultLook() {
    setContent(HomeUiState())

    node(HomeScreenTestTags.SETTINGS_BUTTON).performClick()

    composeTestRule.onNodeWithText(HomeScreenTexts.SETTINGS_COMING_SOON).assertIsDisplayed()
    node(HomeScreenTestTags.SENT_SNACKBAR).assertDoesNotExist()
  }

  @Test
  fun speciesName_isCapitalised() {
    assertEquals("Dog", speciesName(Species.DOG))
    assertEquals("Other", speciesName(Species.OTHER))
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
    const val SNACKBAR_TIMEOUT_MILLIS = 10_000L

    val MILO =
        WaitingSighting(
            id = "s1",
            petName = "Milo",
            species = Species.CAT,
            photoUri = null,
            savedAtMillis = 1_700_000_000_000L,
            place = "Plainpalais, Geneva",
        )
    val STRAY =
        WaitingSighting(
            id = "s2",
            petName = null,
            species = Species.DOG,
            photoUri = "file:///does/not/exist.jpg",
            savedAtMillis = 1_700_000_100_000L,
            place = "46.2044, 6.1432",
        )
  }
}
