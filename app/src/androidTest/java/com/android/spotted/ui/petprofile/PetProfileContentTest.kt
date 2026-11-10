// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.petprofile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.FakePetRepository
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepository
import com.android.spotted.model.Pet.Species
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The pet profile and its Edit action: [PetProfileContent] with hand-built states, then
 * [PetProfileScreen] wired to its real ViewModel over a fake repository.
 */
@RunWith(AndroidJUnit4::class)
class PetProfileContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val luna =
      Pet(
          id = "pet-1",
          ownerId = "owner-1",
          name = "Luna",
          species = Species.CAT,
          note = "White fluffy cat",
          allergies = listOf("Chicken"),
          behaviors = setOf(Behavior.FEARFUL_OF_STRANGERS),
      )

  private val loaded = PetProfileUiState(pet = luna, isLoading = false)

  // --- Content ---

  @Test
  fun petIsShownWithEditAction() {
    composeTestRule.setContent { PetProfileContent(loaded) }

    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.NAME).assertTextEquals("Luna")
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.SPECIES).assertTextEquals("Cat")
    // Lower parts of the profile scroll: on a small screen they are below the visible area
    composeTestRule.onNodeWithText("White fluffy cat").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Fearful of strangers").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Chicken").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Edit Luna").assertIsDisplayed()
  }

  @Test
  fun editAndBackClicksAreForwarded() {
    var edits = 0
    var backs = 0
    composeTestRule.setContent {
      PetProfileContent(loaded, onEdit = { edits++ }, onBack = { backs++ })
    }

    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.EDIT_BUTTON).performClick()
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.BACK_BUTTON).performClick()

    assertEquals(1, edits)
    assertEquals(1, backs)
  }

  @Test
  fun reloadingKeepsThePetShown() {
    composeTestRule.setContent { PetProfileContent(loaded.copy(isLoading = true)) }

    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.NAME).assertTextEquals("Luna")
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.LOADING).assertDoesNotExist()
  }

  @Test
  fun petWithoutDetailsHidesTheDetailsCard() {
    composeTestRule.setContent {
      PetProfileContent(
          PetProfileUiState(pet = Pet("pet-2", "owner-1", "Rex", Species.DOG), isLoading = false)
      )
    }

    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.IMAGE_PLACEHOLDER).assertIsDisplayed()
    composeTestRule.onNodeWithText("ALLERGIES").assertDoesNotExist()
    composeTestRule.onNodeWithText("CHARACTERISTICS").assertDoesNotExist()
  }

  // --- Screen wired to its ViewModel ---

  private val petRepository: PetRepository =
      FakePetRepository().apply { runBlocking { addPet(luna) } }

  private val editedIds = mutableListOf<String>()

  private fun setScreen() {
    val viewModel = PetProfileViewModel(petRepository)
    composeTestRule.setContent {
      PetProfileScreen(viewModel = viewModel, petId = luna.id, onEdit = { editedIds += it })
    }
    composeTestRule.waitUntil { hasNode(PetProfileScreenTestTags.NAME) }
  }

  private fun hasNode(tag: String) =
      composeTestRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

  @Test
  fun screen_edit_opensTheEditFormOfThePet() {
    setScreen()

    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.EDIT_BUTTON).performClick()

    assertEquals(listOf(luna.id), editedIds)
  }
}
