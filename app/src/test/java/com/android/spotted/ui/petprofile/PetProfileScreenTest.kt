package com.android.spotted.ui.petprofile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepositoryLocal
import com.android.spotted.model.Pet.Species
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

    composeTestRule.onNodeWithTag("pet_profile_loading").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pet_profile_name").assertDoesNotExist()
    composeTestRule.onNodeWithTag("pet_profile_error").assertDoesNotExist()
  }

  @Test
  fun errorStateShowsMessageInsteadOfSpinner() {
    showContent(PetProfileUiState(isLoading = false, errorMessage = "Pet not found"))

    composeTestRule.onNodeWithTag("pet_profile_error").assertTextEquals("Pet not found")
    composeTestRule.onNodeWithTag("pet_profile_loading").assertDoesNotExist()
    composeTestRule.onNodeWithTag("pet_profile_name").assertDoesNotExist()
  }

  @Test
  fun petWithPhotoShowsDetailsAndImage() {
    showContent(
        PetProfileUiState(
            pet = rex.copy(photoUrl = "https://example.com/rex.jpg"),
            isLoading = false,
        )
    )

    composeTestRule.onNodeWithTag("pet_profile_name").assertTextEquals("Rex")
    composeTestRule.onNodeWithTag("pet_profile_species").assertTextEquals("Species: CAT")
    composeTestRule.onNodeWithTag("pet_profile_image").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pet_profile_image_placeholder").assertDoesNotExist()
  }

  @Test
  fun petWithoutPhotoShowsPlaceholder() {
    showContent(PetProfileUiState(pet = rex, isLoading = false))

    composeTestRule.onNodeWithTag("pet_profile_image_placeholder").assertIsDisplayed()
    composeTestRule.onNodeWithText("No Photo").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pet_profile_image").assertDoesNotExist()
  }

  @Test
  fun screenLoadsPetFromViewModel() {
    val repository = PetRepositoryLocal()
    val viewModel = PetProfileViewModel(repository)
    composeTestRule.setContent { PetProfileScreen(viewModel, petId = "missing") }

    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag("pet_profile_error")
        .assertTextEquals("Pet with id missing was not found")
  }
}
