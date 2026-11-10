// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.editpet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.R
import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.FakePetRepository
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepository
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.photo.FakePhotoRepository
import com.android.spotted.utils.FakeOpenAlertsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The edit form: [EditPetContent] with hand-built states, then [EditPetScreen] wired to its real
 * ViewModel over fake repositories.
 */
@RunWith(AndroidJUnit4::class)
class EditPetContentTest {

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

  private val filled =
      EditPetUiState(
          name = "Luna",
          species = Species.CAT,
          description = "White fluffy cat",
          allergies = listOf("Chicken"),
          behaviors = setOf(Behavior.FEARFUL_OF_STRANGERS),
          isLoading = false,
      )

  private val names = mutableListOf<String>()
  private val speciesSelected = mutableListOf<Species>()
  private val descriptions = mutableListOf<String>()
  private val allergies = mutableListOf<List<String>>()
  private val behaviorsToggled = mutableListOf<Behavior>()
  private var backs = 0
  private var saves = 0

  private fun setContent(state: EditPetUiState) {
    composeTestRule.setContent {
      EditPetContent(
          state = state,
          onBack = { backs++ },
          onNameChange = { names += it },
          onSpeciesSelect = { speciesSelected += it },
          onPhotoChange = {},
          onPhotoError = {},
          onDescriptionChange = { descriptions += it },
          onAllergiesChange = { allergies += it },
          onBehaviorToggle = { behaviorsToggled += it },
          onSave = { saves++ },
      )
    }
  }

  // --- Content ---

  @Test
  fun fieldsStartFilledWithThePet() {
    setContent(filled)

    composeTestRule.onNodeWithText("Edit Luna").assertIsDisplayed()
    composeTestRule.onNodeWithTag(EditPetScreenTestTags.NAME_FIELD).assertTextEquals("Luna")
    composeTestRule
        .onNodeWithTag(EditPetScreenTestTags.DESCRIPTION_FIELD)
        .assertTextEquals("White fluffy cat")
    composeTestRule.onNodeWithTag(EditPetScreenTestTags.ALLERGIES_FIELD).assertTextEquals("Chicken")
    composeTestRule.onNodeWithTag(EditPetScreenTestTags.speciesChip(Species.CAT)).assertIsSelected()
    composeTestRule
        .onNodeWithTag(EditPetScreenTestTags.behaviorChip(Behavior.FEARFUL_OF_STRANGERS))
        .performScrollTo()
        .assertIsOn()
    composeTestRule.onNodeWithTag(EditPetScreenTestTags.SAVE_BUTTON).assertIsEnabled()
  }

  @Test
  fun editsAreForwarded() {
    setContent(filled)

    composeTestRule.onNodeWithTag(EditPetScreenTestTags.NAME_FIELD).performTextReplacement("Lulu")
    composeTestRule.onNodeWithTag(EditPetScreenTestTags.speciesChip(Species.DOG)).performClick()
    composeTestRule
        .onNodeWithTag(EditPetScreenTestTags.DESCRIPTION_FIELD)
        .performTextReplacement("Grey")
    composeTestRule
        .onNodeWithTag(EditPetScreenTestTags.behaviorChip(Behavior.STRESSED_IN_CROWDS))
        .performScrollTo()
        .performClick()

    // The state is fixed here, so the field may send its old value back after the edit
    assertEquals("Lulu", names.first())
    assertEquals(listOf(Species.DOG), speciesSelected)
    assertEquals("Grey", descriptions.first())
    assertEquals(listOf(Behavior.STRESSED_IN_CROWDS), behaviorsToggled)
  }

  @Test
  fun allergiesKeepTheTypedCommaAndSendTheList() {
    setContent(filled)

    val field = composeTestRule.onNodeWithTag(EditPetScreenTestTags.ALLERGIES_FIELD)
    field.performTextReplacement("Chicken, ")
    field.assertTextEquals("Chicken, ")
    field.performTextReplacement("Chicken, Fleas")

    assertEquals(listOf("Chicken"), allergies.first())
    assertEquals(listOf("Chicken", "Fleas"), allergies.last())
  }

  @Test
  fun saveAndBackClicksAreForwarded() {
    setContent(filled)

    composeTestRule.onNodeWithTag(EditPetScreenTestTags.SAVE_BUTTON).performClick()
    composeTestRule.onNodeWithTag(EditPetScreenTestTags.BACK_BUTTON).performClick()

    assertEquals(1, saves)
    assertEquals(1, backs)
  }

  @Test
  fun saveIsDisabledWithoutAName() {
    setContent(filled.copy(name = ""))

    composeTestRule.onNodeWithTag(EditPetScreenTestTags.SAVE_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun loadingShowsOnlyTheSpinner() {
    setContent(EditPetUiState())

    composeTestRule.onNodeWithTag(EditPetScreenTestTags.LOADING).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EditPetScreenTestTags.NAME_FIELD).assertDoesNotExist()
  }

  @Test
  fun errorIsShownAboveSave() {
    setContent(filled.copy(errorMessage = R.string.edit_pet_save_error))

    composeTestRule
        .onNodeWithTag(EditPetScreenTestTags.ERROR)
        .assertTextEquals("Unable to save changes to the pet")
  }

  @Test
  fun loadFailure_showsOnlyTheErrorAndBack() {
    setContent(
        EditPetUiState(
            isLoading = false,
            loadFailed = true,
            errorMessage = R.string.edit_pet_load_error,
        )
    )

    composeTestRule
        .onNodeWithTag(EditPetScreenTestTags.ERROR)
        .assertTextEquals("Unable to load the pet")
    composeTestRule.onNodeWithTag(EditPetScreenTestTags.NAME_FIELD).assertDoesNotExist()
    composeTestRule.onNodeWithTag(EditPetScreenTestTags.SAVE_BUTTON).assertDoesNotExist()
    composeTestRule.onNodeWithTag(EditPetScreenTestTags.BACK_BUTTON).performClick()
    assertEquals(1, backs)
  }

  @Test
  fun savingShowsProgressAndDisablesSave() {
    setContent(filled.copy(saving = true))

    composeTestRule.onNodeWithTag(EditPetScreenTestTags.SAVE_BUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithText("Save changes").assertDoesNotExist()
  }

  // --- Screen wired to its ViewModel ---

  private var savedCalls = 0

  private fun setScreen(petRepository: PetRepository): EditPetViewModel {
    val viewModel =
        EditPetViewModel(luna.id, petRepository, FakeOpenAlertsRepository(), FakePhotoRepository())
    composeTestRule.setContent {
      EditPetScreen(petId = luna.id, onBack = {}, onSaved = { savedCalls++ }, viewModel = viewModel)
    }
    composeTestRule.waitUntil {
      composeTestRule
          .onAllNodes(hasTestTag(EditPetScreenTestTags.NAME_FIELD))
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    return viewModel
  }

  @Test
  fun screen_save_updatesThePetAndReturns() {
    val repository = FakePetRepository().apply { runBlocking { addPet(luna) } }
    setScreen(repository)

    composeTestRule.onNodeWithTag(EditPetScreenTestTags.NAME_FIELD).performTextReplacement("Lulu")
    composeTestRule.onNodeWithTag(EditPetScreenTestTags.SAVE_BUTTON).performClick()
    composeTestRule.waitUntil { savedCalls == 1 }

    assertEquals("Lulu", runBlocking { repository.getPet(luna.id).getOrThrow().name })
  }

  @Test
  fun screen_saveFailure_staysOnTheFormWithAnError() {
    val repository =
        FailingUpdateRepository(FakePetRepository().apply { runBlocking { addPet(luna) } })
    setScreen(repository)

    composeTestRule.onNodeWithTag(EditPetScreenTestTags.SAVE_BUTTON).performClick()
    composeTestRule.waitUntil {
      composeTestRule
          .onAllNodes(hasTestTag(EditPetScreenTestTags.ERROR))
          .fetchSemanticsNodes()
          .isNotEmpty()
    }

    assertEquals(0, savedCalls)
  }

  @Test
  fun screen_petNotFound_cannotBeSaved() {
    val viewModel =
        EditPetViewModel(
            luna.id,
            FakePetRepository(),
            FakeOpenAlertsRepository(),
            FakePhotoRepository(),
        )
    composeTestRule.setContent {
      EditPetScreen(petId = luna.id, onBack = {}, onSaved = { savedCalls++ }, viewModel = viewModel)
    }
    composeTestRule.waitUntil {
      composeTestRule
          .onAllNodes(hasTestTag(EditPetScreenTestTags.ERROR))
          .fetchSemanticsNodes()
          .isNotEmpty()
    }

    composeTestRule.onNodeWithTag(EditPetScreenTestTags.SAVE_BUTTON).assertDoesNotExist()
    assertEquals(0, savedCalls)
  }

  @Test
  fun screen_cameraError_isShownOnTheForm() {
    val viewModel = setScreen(FakePetRepository().apply { runBlocking { addPet(luna) } })

    viewModel.onPhotoPickerError()

    composeTestRule
        .onNodeWithTag(EditPetScreenTestTags.ERROR)
        .assertTextEquals("Unable to open the camera. Choose a photo from your gallery instead.")
    composeTestRule.onNodeWithTag(EditPetScreenTestTags.NAME_FIELD).assertIsDisplayed()
    assertEquals(0, savedCalls)
  }

  /** A pet repository whose updates always fail. */
  private class FailingUpdateRepository(private val pets: PetRepository) : PetRepository by pets {
    override suspend fun updatePet(pet: Pet): Result<Unit> =
        Result.failure(IllegalStateException("Update refused"))
  }
}
