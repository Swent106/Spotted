// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.petprofile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.R
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.FakePetRepository
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepository
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.photo.FakePhotoRepository
import com.android.spotted.ui.editpet.EditPetViewModel
import com.android.spotted.utils.FakeOpenAlertsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The pet profile's Edit and Delete actions: [PetProfileContent] with hand-built states, then
 * [PetProfileScreen] wired to its real ViewModels over fake repositories.
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
  fun petIsShownWithEditAndDeleteActions() {
    composeTestRule.setContent { PetProfileContent(loaded) }

    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.NAME).assertTextEquals("Luna")
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.SPECIES).assertTextEquals("Cat")
    // Lower parts of the profile scroll: on a small screen they are below the visible area
    composeTestRule.onNodeWithText("White fluffy cat").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Fearful of strangers").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Chicken").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Edit Luna").assertIsDisplayed()
    composeTestRule.onNodeWithText("Delete Luna").assertIsDisplayed()
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.DELETE_DIALOG).assertDoesNotExist()
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
  fun deleteDialogAsksForConfirmation() {
    var confirms = 0
    var cancels = 0
    composeTestRule.setContent {
      PetProfileContent(
          loaded,
          showDeleteDialog = true,
          onConfirmDelete = { confirms++ },
          onCancelDelete = { cancels++ },
      )
    }

    composeTestRule.onNodeWithText("Delete Luna?").assertIsDisplayed()
    composeTestRule
        .onNodeWithText("Luna’s profile and photo will be removed for good. This can’t be undone.")
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.DELETE_CONFIRM).performClick()
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.DELETE_CANCEL).performClick()

    assertEquals(1, confirms)
    assertEquals(1, cancels)
  }

  @Test
  fun refusedDialogExplainsTheOpenAlert() {
    var dismissals = 0
    composeTestRule.setContent {
      PetProfileContent(loaded, showRefusedDialog = true, onDismissRefused = { dismissals++ })
    }

    composeTestRule.onNodeWithText("Luna is still reported missing").assertIsDisplayed()
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.ALERT_OPEN_OK).performClick()

    assertEquals(1, dismissals)
  }

  @Test
  fun deletingDisablesDeleteAndShowsError() {
    composeTestRule.setContent {
      PetProfileContent(loaded, isDeleting = true, deleteError = R.string.edit_pet_delete_error)
    }

    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.DELETE_BUTTON).assertIsNotEnabled()
    composeTestRule
        .onNodeWithTag(PetProfileScreenTestTags.DELETE_ERROR)
        .assertTextEquals("Unable to delete the pet")
  }

  @Test
  fun deleteUnavailable_disablesDeleteAndShowsTheLoadError() {
    composeTestRule.setContent {
      PetProfileContent(loaded, canDelete = false, deleteError = R.string.edit_pet_load_error)
    }

    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.DELETE_BUTTON).assertIsNotEnabled()
    composeTestRule
        .onNodeWithTag(PetProfileScreenTestTags.DELETE_ERROR)
        .assertTextEquals("Unable to load the pet")
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

  // --- Screen wired to its ViewModels ---

  private val petRepository: PetRepository =
      FakePetRepository().apply { runBlocking { addPet(luna) } }

  private var deleted = 0
  private val editedIds = mutableListOf<String>()
  private val linkedIds = mutableListOf<String>()

  /**
   * [deletePets] is what the delete ViewModel reads the pet from; the profile uses the real one.
   */
  private fun setScreen(
      openAlerts: List<Alert> = emptyList(),
      deletePets: PetRepository = petRepository,
  ) {
    val alerts = FakeOpenAlertsRepository(openAlerts)
    val viewModel = PetProfileViewModel(petRepository)
    val deleteViewModel = EditPetViewModel(luna.id, deletePets, alerts, FakePhotoRepository())
    composeTestRule.setContent {
      PetProfileScreen(
          viewModel = viewModel,
          petId = luna.id,
          deleteViewModel = deleteViewModel,
          onEdit = { editedIds += it },
          onLinkTag = { linkedIds += it },
          onDeleted = { deleted++ },
      )
    }
    composeTestRule.waitUntil { hasNode(PetProfileScreenTestTags.NAME) }
  }

  private fun hasNode(tag: String) =
      composeTestRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

  private fun petStillExists() = runBlocking { petRepository.getPet(luna.id).isSuccess }

  @Test
  fun screen_edit_opensTheEditFormOfThePet() {
    setScreen()

    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.EDIT_BUTTON).performClick()

    assertEquals(listOf(luna.id), editedIds)
  }

  @Test
  fun screen_linkTag_opensTheLinkTagScreenOfThePet() {
    setScreen()

    composeTestRule
        .onNodeWithTag(PetProfileScreenTestTags.LINK_TAG_BUTTON)
        .assertTextEquals("Link a tag")
        .performClick()

    assertEquals(listOf(luna.id), linkedIds)
  }

  @Test
  fun screen_deleteConfirmed_deletesThePetAndLeaves() {
    setScreen()

    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.DELETE_BUTTON).performClick()
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.DELETE_CONFIRM).performClick()
    composeTestRule.waitUntil { deleted == 1 }

    assertFalse(petStillExists())
  }

  @Test
  fun screen_deletePetCannotBeLoaded_disablesDeleteAndShowsWhy() {
    val unreadable =
        object : PetRepository by petRepository {
          override suspend fun getPet(id: String): Result<Pet> =
              Result.failure(IllegalStateException("Read refused"))
        }
    setScreen(deletePets = unreadable)
    composeTestRule.waitUntil { hasNode(PetProfileScreenTestTags.DELETE_ERROR) }

    composeTestRule
        .onNodeWithTag(PetProfileScreenTestTags.DELETE_ERROR)
        .assertTextEquals("Unable to load the pet")
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.DELETE_BUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.DELETE_BUTTON).performClick()
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.DELETE_DIALOG).assertDoesNotExist()
    assertTrue(petStillExists())
  }

  @Test
  fun screen_deleteCancelled_doesNothing() {
    setScreen()

    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.DELETE_BUTTON).performClick()
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.DELETE_CANCEL).performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.DELETE_DIALOG).assertDoesNotExist()
    assertEquals(0, deleted)
    assertTrue(petStillExists())
  }

  @Test
  fun screen_deleteRefused_whenThePetHasAnOpenAlert() {
    setScreen(openAlerts = listOf(openAlertFor(luna)))

    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.DELETE_BUTTON).performClick()
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.DELETE_CONFIRM).performClick()
    composeTestRule.waitUntil { hasNode(PetProfileScreenTestTags.ALERT_OPEN_DIALOG) }
    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.ALERT_OPEN_OK).performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(PetProfileScreenTestTags.ALERT_OPEN_DIALOG).assertDoesNotExist()
    assertEquals(0, deleted)
    assertTrue(petStillExists())
  }

  private fun openAlertFor(pet: Pet) =
      Alert(
          id = "alert-1",
          petId = pet.id,
          ownerId = pet.ownerId,
          lastKnownLocation = Location(46.5197, 6.6323),
          lostAtMillis = 1_700_000_000_000L,
          petName = pet.name,
          petSpecies = pet.species,
      )
}
