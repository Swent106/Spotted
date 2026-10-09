package com.android.spotted.ui.addpets

import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepository
import com.android.spotted.model.Pet.PetRepositoryLocal
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.auth.User
import com.android.spotted.model.photo.FakePhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddPetsViewModelTest {
  private val testDispatcher = StandardTestDispatcher()
  private val user = User(uid = "owner-1", email = "jamie@example.com")
  private lateinit var petRepository: PetRepositoryLocal
  private lateinit var photoRepository: FakePhotoRepository
  private lateinit var viewModel: AddPetsViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    petRepository = PetRepositoryLocal()
    photoRepository = FakePhotoRepository()
    viewModel = AddPetsViewModel(user, petRepository, photoRepository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun initialState_isEmptyAndCannotSave() {
    val state = viewModel.uiState.value

    assertEquals(AddPetsUiState(), state)
    assertFalse(state.canSave)
    assertFalse(state.hasFormInput)
  }

  @Test
  fun fieldChanges_areReflectedInState() {
    viewModel.onNameChanged("Milo")
    viewModel.onSpeciesChanged(Species.CAT)
    viewModel.onBreedChanged("Tabby")
    viewModel.onPhotoChanged("content://photos/1")
    viewModel.onDescriptionChanged("Orange, white paws")
    viewModel.onAllergiesChanged(listOf("Chicken"))
    viewModel.onBehaviorToggled(Behavior.FEARFUL_OF_STRANGERS)

    val state = viewModel.uiState.value
    assertEquals("Milo", state.name)
    assertEquals(Species.CAT, state.species)
    assertEquals("Tabby", state.breed)
    assertEquals("content://photos/1", state.photoUri)
    assertEquals("Orange, white paws", state.description)
    assertEquals(listOf("Chicken"), state.allergies)
    assertEquals(setOf(Behavior.FEARFUL_OF_STRANGERS), state.behaviors)
    assertTrue(state.canSave)
  }

  @Test
  fun onBehaviorToggled_twice_removesBehavior() {
    viewModel.onBehaviorToggled(Behavior.STRESSED_IN_CROWDS)
    viewModel.onBehaviorToggled(Behavior.STRESSED_IN_CROWDS)

    assertTrue(viewModel.uiState.value.behaviors.isEmpty())
  }

  @Test
  fun canSave_isFalseWithBlankName() {
    viewModel.onNameChanged("   ")
    viewModel.onSpeciesChanged(Species.DOG)

    assertFalse(viewModel.uiState.value.canSave)
  }

  @Test
  fun canSave_isFalseWithoutSpecies() {
    viewModel.onNameChanged("Rex")

    assertFalse(viewModel.uiState.value.canSave)
  }

  @Test
  fun savePet_withoutSpecies_showsErrorAndSavesNothing() = runTest {
    viewModel.onNameChanged("Rex")

    viewModel.savePet()
    advanceUntilIdle()

    assertEquals(AddPetsViewModel.MISSING_FIELDS_MESSAGE, viewModel.uiState.value.errorMessage)
    assertTrue(savedPets().isEmpty())
  }

  @Test
  fun savePet_savesPetForUserAndClearsForm() = runTest {
    fillForm(name = "  Rex  ", species = Species.DOG)
    viewModel.onDescriptionChanged("Brown, red collar ")

    viewModel.savePet()
    advanceUntilIdle()

    val saved = savedPets().single()
    assertEquals("Rex", saved.name)
    assertEquals(Species.DOG, saved.species)
    assertEquals("Brown, red collar", saved.note)
    assertNull(saved.photoUrl)
    val state = viewModel.uiState.value
    assertEquals(listOf(saved), state.addedPets)
    assertFalse(state.hasFormInput)
    assertFalse(state.isSaving)
    assertFalse(state.isDone)
  }

  @Test
  fun savePet_severalTimes_addsSeveralPets() = runTest {
    fillForm(name = "Rex", species = Species.DOG)
    viewModel.savePet()
    advanceUntilIdle()
    fillForm(name = "Milo", species = Species.CAT)
    viewModel.savePet()
    advanceUntilIdle()

    assertEquals(listOf("Rex", "Milo"), viewModel.uiState.value.addedPets.map(Pet::name))
    assertEquals(2, savedPets().size)
  }

  @Test
  fun savePet_withPhoto_uploadsPhotoAndStoresItsUrl() = runTest {
    fillForm(name = "Milo", species = Species.CAT)
    viewModel.onPhotoChanged("content://photos/milo")

    viewModel.savePet()
    advanceUntilIdle()

    val saved = savedPets().single()
    val photoUrl = saved.photoUrl
    assertNotNull(photoUrl)
    assertEquals("content://photos/milo", photoRepository.uploadedPhotos[photoUrl])
    assertTrue(photoUrl.orEmpty().contains("${user.uid}/${saved.id}"))
  }

  @Test
  fun savePet_whenPhotoUploadFails_keepsFormAndSavesNothing() = runTest {
    photoRepository.failure = IllegalStateException("Network down")
    fillForm(name = "Milo", species = Species.CAT)
    viewModel.onPhotoChanged("content://photos/milo")

    viewModel.savePet()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals("Network down", state.errorMessage)
    assertEquals("Milo", state.name)
    assertEquals("content://photos/milo", state.photoUri)
    assertFalse(state.isSaving)
    assertTrue(savedPets().isEmpty())
  }

  @Test
  fun savePet_whenSaveFails_keepsFormAndShowsError() = runTest {
    viewModel = viewModelWithFailingSave(IllegalStateException("Denied"))
    fillForm(name = "Rex", species = Species.DOG)

    viewModel.savePet()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals("Denied", state.errorMessage)
    assertEquals("Rex", state.name)
    assertTrue(state.addedPets.isEmpty())
    assertFalse(state.isSaving)
  }

  @Test
  fun savePet_whenFailureHasNoMessage_showsFallbackMessage() = runTest {
    viewModel = viewModelWithFailingSave(RuntimeException())
    fillForm(name = "Rex", species = Species.DOG)

    viewModel.savePet()
    advanceUntilIdle()

    assertEquals(AddPetsViewModel.SAVE_ERROR_MESSAGE, viewModel.uiState.value.errorMessage)
  }

  @Test
  fun savePet_doubleTap_savesOnlyOnce() = runTest {
    fillForm(name = "Rex", species = Species.DOG)

    viewModel.savePet()
    viewModel.savePet()
    assertTrue(viewModel.uiState.value.isSaving)
    advanceUntilIdle()

    assertEquals(1, savedPets().size)
  }

  @Test
  fun savePet_whenNotSignedIn_showsErrorAndSavesNothing() = runTest {
    viewModel = AddPetsViewModel(null, petRepository, photoRepository)
    fillForm(name = "Rex", species = Species.DOG)

    viewModel.savePet()
    advanceUntilIdle()

    assertEquals(AddPetsViewModel.NOT_SIGNED_IN_MESSAGE, viewModel.uiState.value.errorMessage)
    assertFalse(viewModel.uiState.value.isSaving)
  }

  @Test
  fun editingAfterError_clearsError() = runTest {
    viewModel.savePet()
    advanceUntilIdle()

    viewModel.onNameChanged("Rex")

    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun skip_endsStepWithoutSaving() = runTest {
    fillForm(name = "Rex", species = Species.DOG)

    viewModel.skip()
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.isDone)
    assertTrue(savedPets().isEmpty())
  }

  @Test
  fun finish_withEmptyForm_endsStep() {
    viewModel.finish()

    assertTrue(viewModel.uiState.value.isDone)
  }

  @Test
  fun finish_withFilledForm_savesPetThenEndsStep() = runTest {
    fillForm(name = "Rex", species = Species.DOG)

    viewModel.finish()
    assertFalse(viewModel.uiState.value.isDone)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state.isDone)
    assertEquals(listOf("Rex"), state.addedPets.map(Pet::name))
  }

  @Test
  fun finish_withIncompleteForm_showsErrorAndStays() = runTest {
    viewModel.onDescriptionChanged("Brown dog")

    viewModel.finish()
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isDone)
    assertEquals(AddPetsViewModel.MISSING_FIELDS_MESSAGE, viewModel.uiState.value.errorMessage)
  }

  @Test
  fun finish_whenSaveFails_staysOnStep() = runTest {
    viewModel = viewModelWithFailingSave(IllegalStateException("Denied"))
    fillForm(name = "Rex", species = Species.DOG)

    viewModel.finish()
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isDone)
    assertEquals("Denied", viewModel.uiState.value.errorMessage)
  }

  private fun fillForm(name: String, species: Species) {
    viewModel.onNameChanged(name)
    viewModel.onSpeciesChanged(species)
  }

  private suspend fun savedPets(): List<Pet> = petRepository.getPetsByOwner(user.uid).getOrThrow()

  private fun viewModelWithFailingSave(error: Throwable) =
      AddPetsViewModel(user, FailingPetRepository(error), photoRepository)

  /** A pet repository whose saves always fail, to test error handling. */
  private class FailingPetRepository(private val error: Throwable) :
      PetRepository by PetRepositoryLocal() {
    override suspend fun addPet(pet: Pet): Result<Unit> = Result.failure(error)
  }
}
