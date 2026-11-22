// Portions of this code were generated with the help of Copilot.
// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.addpets

import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.Lifestyle
import com.android.spotted.model.Pet.Microchipped
import com.android.spotted.model.Pet.Neutered
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepository
import com.android.spotted.model.Pet.PetRepositoryLocal
import com.android.spotted.model.Pet.PetSize
import com.android.spotted.model.Pet.Sex
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.auth.User
import com.android.spotted.model.photo.FakePhotoRepository
import java.time.Instant
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

/** Mid-June 2026, far from New Year in any time zone. */
private val MID_2026 = Instant.parse("2026-06-15T12:00:00Z").toEpochMilli()

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
    viewModel = AddPetsViewModel(user, petRepository, photoRepository, now = { MID_2026 })
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
  fun onAgeChanged_isKeptOnScreenAndClearedAfterSaving() = runTest {
    fillForm(name = "Rex", species = Species.DOG)
    viewModel.onAgeChanged("4 years")
    assertEquals("4 years", viewModel.uiState.value.age)

    viewModel.savePet()
    advanceUntilIdle()

    assertEquals("", viewModel.uiState.value.age)
    assertEquals("Rex", savedPets().single().name)
  }

  @Test
  fun canFinish_isFalseWithOnlyAnAge() {
    viewModel.onAgeChanged("4 years")

    assertTrue(viewModel.uiState.value.hasFormInput)
    assertFalse(viewModel.uiState.value.canFinish)
  }

  @Test
  fun onDoneHandled_resetsIsDone() {
    viewModel.skip()
    assertTrue(viewModel.uiState.value.isDone)

    viewModel.onDoneHandled()

    assertFalse(viewModel.uiState.value.isDone)
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
  fun canFinish_isTrueWithEmptyForm() {
    assertTrue(viewModel.uiState.value.canFinish)
  }

  @Test
  fun canFinish_isFalseWithIncompleteForm() {
    viewModel.onDescriptionChanged("Brown dog")

    assertFalse(viewModel.uiState.value.canFinish)
  }

  @Test
  fun canFinish_isTrueWithCompleteForm() {
    fillForm(name = "Rex", species = Species.DOG)

    assertTrue(viewModel.uiState.value.canFinish)
  }

  @Test
  fun savePet_withoutSpecies_savesNothing() = runTest {
    viewModel.onNameChanged("Rex")

    viewModel.savePet()
    advanceUntilIdle()

    assertTrue(savedPets().isEmpty())
    assertFalse(viewModel.uiState.value.isSaving)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun savePet_savesPetForUserAndClearsForm() = runTest {
    fillForm(name = "  Rex  ", species = Species.DOG)
    viewModel.onBreedChanged(" Labrador ")
    viewModel.onDescriptionChanged("Brown, red collar ")
    viewModel.onAllergiesChanged(listOf("Chicken", "Pollen"))
    viewModel.onBehaviorToggled(Behavior.STRESSED_IN_CROWDS)
    viewModel.onBehaviorToggled(Behavior.FEARFUL_OF_STRANGERS)

    viewModel.savePet()
    advanceUntilIdle()

    val saved = savedPets().single()
    assertEquals(user.uid, saved.ownerId)
    assertEquals("Rex", saved.name)
    assertEquals(Species.DOG, saved.species)
    assertEquals("Labrador", saved.breed)
    assertEquals("Brown, red collar", saved.note)
    assertEquals(listOf("Chicken", "Pollen"), saved.allergies)
    assertEquals(
        setOf(Behavior.STRESSED_IN_CROWDS, Behavior.FEARFUL_OF_STRANGERS),
        saved.behaviors,
    )
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
  fun savePet_whenPhotoUploadFails_savesThePetWithoutPhotoAndSaysSo() = runTest {
    photoRepository.failure = IllegalStateException("Object does not exist at location")
    fillForm(name = "Milo", species = Species.CAT)
    viewModel.onPhotoChanged("content://photos/milo")

    viewModel.savePet()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(
        AddPetsMessage.Resource(com.android.spotted.R.string.add_pets_photo_upload_error),
        state.errorMessage,
    )
    assertFalse(state.isSaving)
    val saved = savedPets().single()
    assertEquals("Milo", saved.name)
    assertNull(saved.photoUrl)
  }

  @Test
  fun savePet_whenSaveFails_keepsFormAndShowsError() = runTest {
    viewModel = viewModelWithFailingSave(IllegalStateException("Denied"))
    fillForm(name = "Rex", species = Species.DOG)

    viewModel.savePet()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(
        AddPetsMessage.Resource(com.android.spotted.R.string.add_pets_save_error),
        state.errorMessage,
    )
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

    assertEquals(
        AddPetsMessage.Resource(com.android.spotted.R.string.add_pets_save_error),
        viewModel.uiState.value.errorMessage,
    )
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
  fun savePet_whenNotSignedIn_asksToSignInAndSavesNothing() = runTest {
    viewModel = AddPetsViewModel(null, petRepository, photoRepository)
    fillForm(name = "Rex", species = Species.DOG)

    viewModel.savePet()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state.needsSignIn)
    assertEquals(
        AddPetsMessage.Resource(com.android.spotted.R.string.add_pets_not_signed_in),
        state.errorMessage,
    )
    assertFalse(state.isSaving)
    assertTrue(savedPets().isEmpty())
  }

  @Test
  fun editingAfterError_clearsError() = runTest {
    viewModel = viewModelWithFailingSave(IllegalStateException("Denied"))
    fillForm(name = "Rex", species = Species.DOG)
    viewModel.savePet()
    advanceUntilIdle()
    assertEquals(
        AddPetsMessage.Resource(com.android.spotted.R.string.add_pets_save_error),
        viewModel.uiState.value.errorMessage,
    )

    viewModel.onNameChanged("Rex")

    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun photoPickerError_showsLocalizedCameraUnavailableMessage() {
    viewModel.onPhotoPickerError()

    assertEquals(
        AddPetsMessage.Resource(com.android.spotted.R.string.photo_camera_unavailable),
        viewModel.uiState.value.errorMessage,
    )
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
  fun finish_withIncompleteForm_staysAndSavesNothing() = runTest {
    viewModel.onDescriptionChanged("Brown dog")

    viewModel.finish()
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isDone)
    assertTrue(savedPets().isEmpty())
  }

  @Test
  fun finish_whenSaveFails_staysOnStep() = runTest {
    viewModel = viewModelWithFailingSave(IllegalStateException("Denied"))
    fillForm(name = "Rex", species = Species.DOG)

    viewModel.finish()
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isDone)
    assertEquals(
        AddPetsMessage.Resource(com.android.spotted.R.string.add_pets_save_error),
        viewModel.uiState.value.errorMessage,
    )
  }

  @Test
  fun newFieldChanges_areReflectedInState() {
    viewModel.onBirthYearChanged(2021)
    viewModel.onBreedIdChanged("beagle")
    viewModel.onSexChanged(Sex.FEMALE)
    viewModel.onNeuteredChanged(Neutered.YES)
    viewModel.onSizeChanged(PetSize.MEDIUM)
    viewModel.onLifestyleChanged(Lifestyle.INDOOR_AND_OUTDOOR)
    viewModel.onMicrochippedChanged(Microchipped.NO)

    val state = viewModel.uiState.value
    assertEquals(2021, state.birthYear)
    assertNull(state.birthYearError)
    assertEquals("beagle", state.breedId)
    assertEquals(Sex.FEMALE, state.sex)
    assertEquals(Neutered.YES, state.neutered)
    assertEquals(PetSize.MEDIUM, state.size)
    assertEquals(Lifestyle.INDOOR_AND_OUTDOOR, state.lifestyle)
    assertEquals(Microchipped.NO, state.microchipped)
  }

  @Test
  fun eachNewField_countsAsFormInput() {
    val setters: List<AddPetsViewModel.() -> Unit> =
        listOf(
            { onBirthYearChanged(2021) },
            { onBreedIdChanged("beagle") },
            { onSexChanged(Sex.MALE) },
            { onNeuteredChanged(Neutered.NO) },
            { onSizeChanged(PetSize.LARGE) },
            { onLifestyleChanged(Lifestyle.OUTDOOR_ONLY) },
            { onMicrochippedChanged(Microchipped.YES) },
        )
    setters.forEach { set ->
      val fresh = AddPetsViewModel(user, petRepository, photoRepository, now = { MID_2026 })
      fresh.set()
      assertTrue(fresh.uiState.value.hasFormInput)
      // Started but without a name and species: the step can't end without losing it
      assertFalse(fresh.uiState.value.canFinish)
    }
  }

  @Test
  fun savePet_sendsTheNewFieldsAndClearsThem() = runTest {
    fillForm(name = "Rex", species = Species.DOG)
    viewModel.onBirthYearChanged(2021)
    viewModel.onBreedIdChanged("beagle")
    viewModel.onSexChanged(Sex.MALE)
    viewModel.onNeuteredChanged(Neutered.YES)
    viewModel.onSizeChanged(PetSize.LARGE)
    viewModel.onLifestyleChanged(Lifestyle.INDOOR_ONLY)
    viewModel.onMicrochippedChanged(Microchipped.YES)

    viewModel.savePet()
    advanceUntilIdle()

    val saved = savedPets().single()
    assertEquals(2021, saved.birthYear)
    assertEquals("beagle", saved.breedId)
    assertEquals(Sex.MALE, saved.sex)
    assertEquals(Neutered.YES, saved.neutered)
    assertEquals(PetSize.LARGE, saved.size)
    assertEquals(Lifestyle.INDOOR_ONLY, saved.lifestyle)
    assertEquals(Microchipped.YES, saved.microchipped)
    val state = viewModel.uiState.value
    assertNull(state.birthYear)
    assertNull(state.breedId)
    assertEquals(Sex.UNKNOWN, state.sex)
    assertFalse(state.hasFormInput)
  }

  @Test
  fun savePet_withNoneOfTheNewFields_savesThemAsUnknown() = runTest {
    fillForm(name = "Rex", species = Species.DOG)

    viewModel.savePet()
    advanceUntilIdle()

    val saved = savedPets().single()
    assertNull(saved.birthYear)
    assertNull(saved.breedId)
    assertEquals(Sex.UNKNOWN, saved.sex)
    assertEquals(Neutered.UNKNOWN, saved.neutered)
    assertEquals(PetSize.UNKNOWN, saved.size)
    assertEquals(Lifestyle.UNKNOWN, saved.lifestyle)
    assertEquals(Microchipped.UNKNOWN, saved.microchipped)
  }

  @Test
  fun birthYear_inTheFuture_isRefusedAndBlocksSaving() = runTest {
    fillForm(name = "Rex", species = Species.DOG)

    viewModel.onBirthYearChanged(2027)

    val state = viewModel.uiState.value
    assertEquals(2027, state.birthYear)
    assertEquals(
        AddPetsMessage.Resource(
            com.android.spotted.R.string.add_pets_birth_year_invalid,
            listOf(AddPetsViewModel.MIN_BIRTH_YEAR, 2026),
        ),
        state.birthYearError,
    )
    assertFalse(state.canSave)

    viewModel.savePet()
    advanceUntilIdle()
    assertTrue(savedPets().isEmpty())
  }

  @Test
  fun birthYear_beforeTheMinimum_isRefused() {
    viewModel.onBirthYearChanged(AddPetsViewModel.MIN_BIRTH_YEAR - 1)

    assertNotNull(viewModel.uiState.value.birthYearError)
  }

  @Test
  fun birthYear_atTheBounds_isAccepted() {
    viewModel.onBirthYearChanged(AddPetsViewModel.MIN_BIRTH_YEAR)
    assertNull(viewModel.uiState.value.birthYearError)

    viewModel.onBirthYearChanged(2026)
    assertNull(viewModel.uiState.value.birthYearError)
  }

  @Test
  fun birthYear_fixedOrCleared_removesTheErrorAndAllowsSaving() {
    fillForm(name = "Rex", species = Species.DOG)
    viewModel.onBirthYearChanged(2030)
    assertFalse(viewModel.uiState.value.canSave)

    viewModel.onBirthYearChanged(2020)
    assertNull(viewModel.uiState.value.birthYearError)
    assertTrue(viewModel.uiState.value.canSave)

    viewModel.onBirthYearChanged(2030)
    viewModel.onBirthYearChanged(null)
    assertNull(viewModel.uiState.value.birthYear)
    assertNull(viewModel.uiState.value.birthYearError)
    assertTrue(viewModel.uiState.value.canSave)
  }

  @Test
  fun changingTheSpecies_clearsThePickedBreed() {
    viewModel.onSpeciesChanged(Species.DOG)
    viewModel.onBreedIdChanged("beagle")

    viewModel.onSpeciesChanged(Species.DOG)
    assertEquals("beagle", viewModel.uiState.value.breedId)

    viewModel.onSpeciesChanged(Species.CAT)
    assertNull(viewModel.uiState.value.breedId)
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

  @Test
  fun samePetAddedWithPlusThenSavedAgain_isNotSavedTwice() = runTest {
    fillForm(name = "Rex", species = Species.DOG)
    viewModel.savePet()
    advanceUntilIdle()

    fillForm(name = "rex", species = Species.DOG)
    viewModel.finish()
    advanceUntilIdle()

    assertEquals(1, savedPets().size)
    assertTrue(viewModel.uiState.value.isDone)
  }

  @Test
  fun missingField_asksForTheNameWhenOnlyTheAnimalIsChosen() {
    assertEquals(null, viewModel.uiState.value.missingField)

    viewModel.onSpeciesChanged(Species.CAT)

    assertEquals(
        AddPetsMessage.Resource(com.android.spotted.R.string.add_pets_missing_name),
        viewModel.uiState.value.missingField,
    )
  }

  @Test
  fun missingField_asksForTheAnimalOnceTheNameIsTyped() {
    viewModel.onNameChanged("Milo")
    assertEquals(
        AddPetsMessage.Resource(
            com.android.spotted.R.string.add_pets_missing_species,
            listOf("Milo"),
        ),
        viewModel.uiState.value.missingField,
    )

    viewModel.onSpeciesChanged(Species.CAT)

    assertEquals(null, viewModel.uiState.value.missingField)
  }
}
