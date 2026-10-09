package com.android.spotted.ui.addpet

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepository
import com.android.spotted.model.Pet.PetRepositoryLocal
import com.android.spotted.model.photo.FakePhotoRepository
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Answers the photo picker with [pickedPhoto] instead of opening the system UI. */
private class FakeRegistryOwner(private val pickedPhoto: Uri?) : ActivityResultRegistryOwner {
  override val activityResultRegistry =
      object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
          dispatchResult(requestCode, pickedPhoto)
        }
      }
}

/** Pet repository whose [addPet] is replaced by [onAdd]. */
private class StubPetRepository(private val onAdd: suspend (Pet) -> Result<Unit>) :
    PetRepository by PetRepositoryLocal() {
  override suspend fun addPet(pet: Pet): Result<Unit> = onAdd(pet)
}

@RunWith(AndroidJUnit4::class)
class AddPetScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val petRepository = PetRepositoryLocal()
  private val photoRepository = FakePhotoRepository()
  private val photo = Uri.parse("content://media/photo/1")
  private var saveSuccessCalls = 0

  private fun showScreen(pets: PetRepository = petRepository, pickedPhoto: Uri? = null) {
    val viewModel = AddPetViewModel(pets, photoRepository)
    composeTestRule.setContent {
      CompositionLocalProvider(
          LocalActivityResultRegistryOwner provides FakeRegistryOwner(pickedPhoto)
      ) {
        AddPetScreen(viewModel, ownerId = "owner-1", onSaveSuccess = { saveSuccessCalls++ })
      }
    }
  }

  private fun save(name: String = "Rex", withPhoto: Boolean = false) {
    if (withPhoto) composeTestRule.onNodeWithTag("add_pet_photo_button").performClick()
    composeTestRule.onNodeWithTag("add_pet_name_input").performTextInput(name)
    composeTestRule.onNodeWithTag("add_pet_save_button").performClick()
    composeTestRule.waitForIdle()
  }

  private fun savedPet() = runBlocking { petRepository.getPetsByOwner("owner-1").getOrThrow() }

  @Test
  fun saveButtonIsEnabledOnlyOnceNameIsEntered() {
    showScreen()

    composeTestRule.onNodeWithTag("add_pet_save_button").assertIsNotEnabled()
    composeTestRule.onNodeWithTag("add_pet_name_input").performTextInput("Rex")
    composeTestRule.onNodeWithTag("add_pet_save_button").assertIsEnabled()
  }

  @Test
  fun savingWithoutPhotoStoresPetAndCallsOnSaveSuccess() {
    showScreen()

    save()

    assertEquals(1, saveSuccessCalls)
    assertEquals(null, savedPet().single().photoUrl)
  }

  @Test
  fun pickedPhotoIsUploadedAndItsUrlStoredOnPet() {
    showScreen(pickedPhoto = photo)
    composeTestRule.onNodeWithTag("add_pet_photo_button").assertTextEquals("Select Photo")

    save(withPhoto = true)

    composeTestRule.onNodeWithTag("add_pet_photo_button").assertTextEquals("Photo Selected")
    val (url, localUri) = photoRepository.uploadedPhotos.entries.single()
    assertEquals(photo.toString(), localUri)
    assertEquals(url, savedPet().single().photoUrl)
    assertEquals(1, saveSuccessCalls)
  }

  @Test
  fun cancelledPickerKeepsNoPhotoSelected() {
    showScreen(pickedPhoto = null)

    composeTestRule.onNodeWithTag("add_pet_photo_button").performClick()

    composeTestRule.onNodeWithTag("add_pet_photo_button").assertTextEquals("Select Photo")
  }

  @Test
  fun uploadFailureShowsErrorAndDoesNotSavePet() {
    photoRepository.failure = Exception("Upload failed")
    showScreen(pickedPhoto = photo)

    save(withPhoto = true)

    composeTestRule.onNodeWithText("Upload failed").assertIsDisplayed()
    assertEquals(emptyList<Pet>(), savedPet())
    assertEquals(0, saveSuccessCalls)
  }

  @Test
  fun repositoryFailureShowsError() {
    showScreen(pets = StubPetRepository { Result.failure(Exception("Network down")) })

    save()

    composeTestRule.onNodeWithText("Network down").assertIsDisplayed()
    assertEquals(0, saveSuccessCalls)
  }

  @Test
  fun repositoryExceptionShowsError() {
    showScreen(pets = StubPetRepository { throw IllegalStateException("Boom") })

    save()

    composeTestRule.onNodeWithText("Boom").assertIsDisplayed()
  }

  @Test
  fun showsLoadingInsteadOfSaveButtonWhileSaving() {
    showScreen(pets = StubPetRepository { awaitCancellation() })

    save()

    composeTestRule.onNodeWithTag("add_pet_loading").assertIsDisplayed()
    composeTestRule.onNodeWithTag("add_pet_save_button").assertDoesNotExist()
  }
}
