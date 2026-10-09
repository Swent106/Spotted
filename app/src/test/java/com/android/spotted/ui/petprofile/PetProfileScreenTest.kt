package com.android.spotted.ui.petprofile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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

  private val petRepository = PetRepositoryLocal()

  private fun showProfile(petId: String = "pet-1", storedPhotoUrl: String? = null) {
    runBlocking {
      petRepository.addPet(Pet("pet-1", "owner-1", "Rex", Species.CAT, photoUrl = storedPhotoUrl))
    }
    val viewModel = PetProfileViewModel(petRepository)
    composeTestRule.setContent { PetProfileScreen(viewModel, petId) }
  }

  @Test
  fun unknownPetKeepsShowingLoading() {
    showProfile(petId = "missing")

    composeTestRule.onNodeWithTag("pet_profile_loading").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pet_profile_name").assertDoesNotExist()
  }

  @Test
  fun petWithPhotoShowsDetailsAndImage() {
    showProfile(storedPhotoUrl = "https://example.com/rex.jpg")

    composeTestRule.onNodeWithTag("pet_profile_name").assertIsDisplayed()
    composeTestRule.onNodeWithText("Rex").assertIsDisplayed()
    composeTestRule.onNodeWithText("Species: CAT").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pet_profile_image").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pet_profile_image_placeholder").assertDoesNotExist()
  }

  @Test
  fun petWithoutPhotoShowsPlaceholder() {
    showProfile(storedPhotoUrl = null)

    composeTestRule.onNodeWithTag("pet_profile_image_placeholder").assertIsDisplayed()
    composeTestRule.onNodeWithText("No Photo").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pet_profile_image").assertDoesNotExist()
  }
}
