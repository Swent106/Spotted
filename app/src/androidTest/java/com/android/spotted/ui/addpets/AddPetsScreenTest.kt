// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.addpets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepositoryLocal
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.auth.User
import com.android.spotted.model.photo.FakePhotoRepository
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AddPetsScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val names = mutableListOf<String>()
  private val speciesSelected = mutableListOf<Species>()
  private val descriptions = mutableListOf<String>()
  private val ages = mutableListOf<String>()
  private val behaviorsToggled = mutableListOf<Behavior>()
  private var backClicks = 0
  private var skipClicks = 0
  private var saveAndContinueClicks = 0
  private var addAnotherClicks = 0

  private fun setContent(state: AddPetsUiState) {
    composeTestRule.setContent {
      AddPetsContent(
          state = state,
          onBackClick = { backClicks++ },
          onSkipClick = { skipClicks++ },
          onNameChange = { names += it },
          onSpeciesSelect = { speciesSelected += it },
          onPhotoChange = {},
          onDescriptionChange = { descriptions += it },
          onAgeChange = { ages += it },
          onBehaviorToggle = { behaviorsToggled += it },
          onSaveAndContinueClick = { saveAndContinueClicks++ },
          onAddAnotherClick = { addAnotherClicks++ },
      )
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  private fun saveAndContinue() = node(AddPetsScreenTestTags.SAVE_AND_CONTINUE_BUTTON)

  private fun addAnother() = node(AddPetsScreenTestTags.ADD_ANOTHER_BUTTON)

  private val completeForm = AddPetsUiState(name = "Rex", species = Species.DOG)

  // ---------- Stateless content ----------

  @Test
  fun emptyForm_showsAllSections_andCanContinueWithoutAPet() {
    setContent(AddPetsUiState())

    listOf(
            AddPetsScreenTestTags.BACK_BUTTON,
            AddPetsScreenTestTags.SKIP_BUTTON,
            AddPetsScreenTestTags.NAME_INPUT,
            AddPetsScreenTestTags.speciesChip(Species.DOG),
            PetPhotoPickerTestTags.ADD_BUTTON,
            AddPetsScreenTestTags.DESCRIPTION_INPUT,
            AddPetsScreenTestTags.AGE_INPUT,
            AddPetsScreenTestTags.behaviorChip(Behavior.FEARFUL_OF_STRANGERS),
        )
        .forEach { node(it).performScrollTo().assertIsDisplayed() }
    saveAndContinue().assertIsDisplayed().assertIsEnabled()
    addAnother().assertIsDisplayed().assertIsNotEnabled()
    node(AddPetsScreenTestTags.ADDED_PETS).assertDoesNotExist()
    node(AddPetsScreenTestTags.ERROR_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun incompleteForm_disablesBothSaveButtons() {
    setContent(AddPetsUiState(description = "Brown dog"))

    saveAndContinue().assertIsNotEnabled()
    addAnother().assertIsNotEnabled()
  }

  @Test
  fun completeForm_enablesBothSaveButtons_andClicksCallTheirCallbacks() {
    setContent(completeForm)

    saveAndContinue().assertIsEnabled().performClick()
    addAnother().assertIsEnabled().performClick()

    assertEquals(1, saveAndContinueClicks)
    assertEquals(1, addAnotherClicks)
  }

  @Test
  fun savingState_showsLoader_andDisablesButtonsAndSkip() {
    setContent(completeForm.copy(isSaving = true))

    node(AddPetsScreenTestTags.LOADER).assertIsDisplayed()
    saveAndContinue().assertIsNotEnabled()
    addAnother().assertIsNotEnabled()
    node(AddPetsScreenTestTags.SKIP_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun skipAndBack_callTheirCallbacks() {
    setContent(AddPetsUiState())

    node(AddPetsScreenTestTags.SKIP_BUTTON).performClick()
    node(AddPetsScreenTestTags.BACK_BUTTON).performClick()

    assertEquals(1, skipClicks)
    assertEquals(1, backClicks)
  }

  @Test
  fun speciesChips_showSelection_andReportClicks() {
    setContent(AddPetsUiState(species = Species.CAT))

    node(AddPetsScreenTestTags.speciesChip(Species.CAT)).assertIsSelected()
    node(AddPetsScreenTestTags.speciesChip(Species.DOG)).assertIsNotSelected()
    node(AddPetsScreenTestTags.speciesChip(Species.OTHER)).assertIsNotSelected()

    node(AddPetsScreenTestTags.speciesChip(Species.DOG)).performClick()

    assertEquals(listOf(Species.DOG), speciesSelected)
  }

  @Test
  fun behaviorChips_showSelection_andReportToggles() {
    setContent(AddPetsUiState(behaviors = setOf(Behavior.STRESSED_IN_CROWDS)))

    node(AddPetsScreenTestTags.behaviorChip(Behavior.STRESSED_IN_CROWDS))
        .performScrollTo()
        .assertIsOn()
    node(AddPetsScreenTestTags.behaviorChip(Behavior.FEARFUL_OF_STRANGERS))
        .performScrollTo()
        .assertIsOff()
        .performClick()

    assertEquals(listOf(Behavior.FEARFUL_OF_STRANGERS), behaviorsToggled)
  }

  @Test
  fun typingInTheFields_callsTheirCallbacks() {
    setContent(AddPetsUiState())

    node(AddPetsScreenTestTags.NAME_INPUT).performTextInput("Rex")
    node(AddPetsScreenTestTags.DESCRIPTION_INPUT).performScrollTo().performTextInput("Brown")
    node(AddPetsScreenTestTags.AGE_INPUT).performScrollTo().performTextInput("4 years")

    // The state is fixed in this test, so a field may report its unchanged value again when it
    // loses
    // focus: only the first value is the typed text.
    assertEquals("Rex", names.first())
    assertEquals("Brown", descriptions.first())
    assertEquals("4 years", ages.first())
  }

  @Test
  fun addedPets_areListed() {
    val rex = Pet(id = "1", ownerId = "owner-1", name = "Rex", species = Species.DOG)
    val milo = Pet(id = "2", ownerId = "owner-1", name = "Milo", species = Species.CAT)
    setContent(AddPetsUiState(addedPets = listOf(rex, milo)))

    node(AddPetsScreenTestTags.ADDED_PETS).performScrollTo().assertTextEquals("Added: Rex, Milo")
  }

  @Test
  fun errorMessage_isShown() {
    setContent(completeForm.copy(errorMessage = "Unable to save the pet"))

    node(AddPetsScreenTestTags.ERROR_MESSAGE)
        .performScrollTo()
        .assertTextEquals("Unable to save the pet")
  }

  @Test
  fun photo_isShownWhenChosen() {
    setContent(completeForm.copy(photoUri = "content://test/photo.jpg"))

    node(PetPhotoPickerTestTags.PREVIEW).performScrollTo().assertIsDisplayed()
    node(PetPhotoPickerTestTags.ADD_BUTTON).assertDoesNotExist()
  }

  // ---------- Screen wired to the ViewModel ----------

  private val user = User(uid = "owner-1", email = "jamie@example.com")

  private fun viewModel() = AddPetsViewModel(user, PetRepositoryLocal(), FakePhotoRepository())

  /** Names of the pets saved so far: the ViewModel only lists a pet once the save succeeded. */
  private fun AddPetsViewModel.savedNames() = uiState.value.addedPets.map { it.name }

  @Test
  fun addPetsScreen_withViewModel_addsSeveralPets() {
    val viewModel = viewModel()
    composeTestRule.setContent { AddPetsScreen(viewModel = viewModel) }

    node(AddPetsScreenTestTags.NAME_INPUT).performTextInput("Rex")
    node(AddPetsScreenTestTags.speciesChip(Species.DOG)).performClick()
    addAnother().performClick()
    composeTestRule.waitUntil { viewModel.savedNames() == listOf("Rex") }

    // The form is cleared for the next pet, and the saved one is listed
    node(AddPetsScreenTestTags.ADDED_PETS).performScrollTo().assertTextEquals("Added: Rex")
    addAnother().assertIsNotEnabled()

    node(AddPetsScreenTestTags.NAME_INPUT).performScrollTo().performTextInput("Milo")
    node(AddPetsScreenTestTags.speciesChip(Species.CAT)).performClick()
    addAnother().performClick()
    composeTestRule.waitUntil { viewModel.savedNames() == listOf("Rex", "Milo") }
  }

  @Test
  fun addPetsScreen_callsOnDoneOnce_evenWhenShownAgain() {
    val viewModel = viewModel()
    var doneCalls = 0
    var isShown by mutableStateOf(true)
    composeTestRule.setContent {
      if (isShown) AddPetsScreen(viewModel = viewModel, onDone = { doneCalls++ })
    }

    node(AddPetsScreenTestTags.SKIP_BUTTON).performClick()
    composeTestRule.waitUntil { doneCalls == 1 }

    // Simulates coming back to this screen: it is composed again.
    isShown = false
    composeTestRule.waitForIdle()
    isShown = true
    composeTestRule.waitForIdle()

    assertEquals(1, doneCalls)
  }
}
