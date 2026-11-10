// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.petprofile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepositoryLocal
import com.android.spotted.model.Pet.Species
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PetProfileScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val rex = Pet("pet-1", "owner-1", "Rex", Species.CAT)

  private fun showContent(state: PetProfileUiState) {
    composeTestRule.setContent { PetProfileContent(state) }
  }

  @Test
  fun loadingStateShowsSpinnerOnly() {
    showContent(PetProfileUiState(isLoading = true))

    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.LOADING).assertIsDisplayed()
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.NAME).assertDoesNotExist()
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.ERROR).assertDoesNotExist()
  }

  @Test
  fun errorStateShowsMessageInsteadOfSpinner() {
    showContent(PetProfileUiState(isLoading = false, errorMessage = "Pet not found"))

    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.ERROR).assertTextEquals("Pet not found")
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.LOADING).assertDoesNotExist()
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.NAME).assertDoesNotExist()
  }

  @Test
  fun petWithPhotoShowsDetailsAndImage() {
    showContent(
        PetProfileUiState(
            pet = rex.copy(photoUrl = "https://example.com/rex.jpg"),
            isLoading = false,
        )
    )

    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.NAME).assertTextEquals("Rex")
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.SPECIES).assertTextEquals("Cat")
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.IMAGE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.IMAGE_PLACEHOLDER).assertDoesNotExist()
  }

  @Test
  fun tappingPhoto_opensViewerOnceUntilDismissed() {
    showContent(
        PetProfileUiState(
            pet = rex.copy(photoUrl = "https://example.com/rex.jpg"),
            isLoading = false,
        )
    )

    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.IMAGE).performClick()
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.PHOTO_VIEWER).assertIsDisplayed()
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.PHOTO_VIEWER_IMAGE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.PHOTO_VIEWER_CLOSE).performClick()
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.PHOTO_VIEWER).assertDoesNotExist()

    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.IMAGE).performClick()
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.PHOTO_VIEWER).assertIsDisplayed()
  }

  @Test
  fun petWithoutPhotoShowsPlaceholder() {
    showContent(PetProfileUiState(pet = rex, isLoading = false))

    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.IMAGE_PLACEHOLDER).assertIsDisplayed()
    composeTestRule.onNodeWithText("No Photo").assertIsDisplayed()
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.IMAGE).assertDoesNotExist()
  }

  @Test
  fun headerShowsAFixedTitle_andTheNameOnlyInTheCard() {
    var state by mutableStateOf(PetProfileUiState(isLoading = true))
    composeTestRule.setContent { PetProfileContent(state) }

    composeTestRule.onNodeWithText("Pet profile").assertIsDisplayed()

    state = PetProfileUiState(pet = rex, isLoading = false)

    composeTestRule.onNodeWithText("Pet profile").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Rex").assertCountEquals(1)
  }

  @Test
  fun photoClick_isLabelledForTalkBack() {
    showContent(
        PetProfileUiState(
            pet = rex.copy(photoUrl = "https://example.com/rex.jpg"),
            isLoading = false,
        )
    )

    composeTestRule
        .onNodeWithTag(PetProfileScreenTestTags.IMAGE)
        .assert(
            SemanticsMatcher("click label is View photo") {
              it.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == "View photo"
            }
        )
  }

  @Test
  fun screenReloadsWhenThePetIdChanges() {
    val repository = PetRepositoryLocal()
    runBlocking {
      repository.addPet(rex)
      repository.addPet(Pet("pet-2", "owner-1", "Milo", Species.DOG))
    }
    val viewModel = PetProfileViewModel(repository)
    var petId by mutableStateOf(rex.id)
    composeTestRule.setContent { PetProfileScreen(viewModel, petId = petId) }
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.NAME).assertTextEquals("Rex")

    petId = "pet-2"

    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.NAME).assertTextEquals("Milo")
  }

  @Test
  fun screenLoadsPetFromViewModel() {
    val repository = PetRepositoryLocal()
    val viewModel = PetProfileViewModel(repository)
    composeTestRule.setContent { PetProfileScreen(viewModel, petId = "missing") }

    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(PetProfileScreenTestTags.ERROR)
        .assertTextEquals("Pet with id missing was not found")
  }
}
