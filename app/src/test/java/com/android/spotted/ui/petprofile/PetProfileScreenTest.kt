package com.android.spotted.ui.petprofile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepositoryLocal
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.fakes.FakeAlertRepository
import com.android.spotted.model.photo.FakePhotoRepository
import com.android.spotted.ui.editpet.EditPetViewModel
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
  fun screenLoadsPetFromViewModel() {
    val repository = PetRepositoryLocal()
    val viewModel = PetProfileViewModel(repository)
    val deleteViewModel =
        EditPetViewModel("missing", repository, FakeAlertRepository(), FakePhotoRepository())
    composeTestRule.setContent { PetProfileScreen(viewModel, "missing", deleteViewModel) }

    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(PetProfileScreenTestTags.ERROR)
        .assertTextEquals("Pet with id missing was not found")
  }
}
