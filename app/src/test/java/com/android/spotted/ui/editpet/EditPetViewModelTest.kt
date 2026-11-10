// Portions of this code were generated with the help of Claude (Anthropic).
// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.editpet

import com.android.spotted.R
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepository
import com.android.spotted.model.Pet.PetRepositoryLocal
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertStatus
import com.android.spotted.model.fakes.FakeAlertRepository
import com.android.spotted.model.photo.FakePhotoRepository
import com.android.spotted.model.photo.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditPetViewModelTest {
  private val testDispatcher = StandardTestDispatcher()
  private lateinit var localPetRepository: PetRepositoryLocal
  private lateinit var petRepository: ConfigurablePetRepository
  private lateinit var alertRepository: FakeAlertRepository
  private lateinit var photoRepository: FakePhotoRepository
  private lateinit var photos: RecordingPhotoRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    localPetRepository = PetRepositoryLocal()
    petRepository = ConfigurablePetRepository(localPetRepository)
    alertRepository = FakeAlertRepository()
    photoRepository = FakePhotoRepository()
    photos = RecordingPhotoRepository(photoRepository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun loadPet_populatesTheEditableFields() =
      runTest(testDispatcher) {
        val pet =
            pet()
                .copy(
                    name = "Milo",
                    species = Species.CAT,
                    breed = "Tabby",
                    photoUrl = "firestore://photos/pet-1",
                    allergies = listOf("Pollen"),
                    behaviors = setOf(Behavior.FEARFUL_OF_STRANGERS),
                    note = "Quiet home",
                )

        val viewModel = loadedViewModel(pet)

        val state = viewModel.uiState.value
        assertEquals("Milo", state.name)
        assertEquals(Species.CAT, state.species)
        assertEquals("Tabby", state.breed)
        assertEquals("firestore://photos/pet-1", state.photoUri)
        assertEquals(listOf("Pollen"), state.allergies)
        assertEquals(setOf(Behavior.FEARFUL_OF_STRANGERS), state.behaviors)
        assertEquals("Quiet home", state.description)
        assertTrue(state.canSave)
      }

  @Test
  fun loadPet_whenMissingShowsErrorAndCannotSave() =
      runTest(testDispatcher) {
        val viewModel = EditPetViewModel("missing", petRepository, alertRepository, photos)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.loadFailed)
        assertEquals(R.string.edit_pet_load_error, viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.canSave)
      }

  @Test
  fun canSave_requiresLoadedIdleUndeletedCompleteForm() {
    val completeForm = EditPetUiState(name = "Milo", species = Species.DOG, isLoading = false)

    assertTrue(completeForm.canSave)
    assertFalse(completeForm.copy(isLoading = true).canSave)
    assertFalse(completeForm.copy(loadFailed = true).canSave)
    assertFalse(completeForm.copy(saving = true).canSave)
    assertFalse(completeForm.copy(deleted = true).canSave)
    assertFalse(completeForm.copy(species = null).canSave)
    assertFalse(completeForm.copy(name = " ").canSave)
  }

  @Test
  fun save_updatesFieldsWithoutReuploadingUnchangedPhoto() =
      runTest(testDispatcher) {
        val pet = pet().copy(photoUrl = "firestore://photos/pet-1")
        val viewModel = loadedViewModel(pet)
        viewModel.onNameChanged("  Updated name  ")
        viewModel.onSpeciesChanged(Species.CAT)
        viewModel.onBreedChanged("  New breed ")
        viewModel.onDescriptionChanged(" New note ")
        viewModel.onAllergiesChanged(listOf("Dust"))
        viewModel.onBehaviorToggled(Behavior.STRESSED_IN_CROWDS)

        viewModel.save()
        advanceUntilIdle()

        val updated = localPetRepository.getPet(pet.id).getOrThrow()
        assertEquals("Updated name", updated.name)
        assertEquals(Species.CAT, updated.species)
        assertEquals("New breed", updated.breed)
        assertEquals("New note", updated.note)
        assertEquals(listOf("Dust"), updated.allergies)
        assertEquals(setOf(Behavior.STRESSED_IN_CROWDS), updated.behaviors)
        assertEquals(pet.photoUrl, updated.photoUrl)
        assertTrue(photoRepository.uploadedPhotos.isEmpty())
        assertEquals(0, photos.deleteCalls)
        assertFalse(viewModel.uiState.value.saving)
        assertTrue(viewModel.uiState.value.saved)
        assertNull(viewModel.uiState.value.errorMessage)
      }

  @Test
  fun save_whenPhotoChangesUploadsThenUpdatesThePet() =
      runTest(testDispatcher) {
        val pet = pet().copy(photoUrl = "firestore://photos/pet-1")
        val viewModel = loadedViewModel(pet)
        viewModel.onPhotoChanged("content://photos/new")

        viewModel.save()
        advanceUntilIdle()

        val updated = localPetRepository.getPet(pet.id).getOrThrow()
        assertEquals("content://photos/new", photoRepository.uploadedPhotos[updated.photoUrl])
        assertTrue(updated.photoUrl != pet.photoUrl)
        assertEquals(0, photos.deleteCalls)
        assertNull(viewModel.uiState.value.errorMessage)
      }

  @Test
  fun save_whenPhotoIsRemovedClearsTheUrlAndDeletesThePhoto() =
      runTest(testDispatcher) {
        val pet = pet().copy(photoUrl = "firestore://photos/pet-1")
        val viewModel = loadedViewModel(pet)
        viewModel.onPhotoChanged(null)

        viewModel.save()
        advanceUntilIdle()

        assertNull(localPetRepository.getPet(pet.id).getOrThrow().photoUrl)
        assertEquals(listOf(pet.ownerId to pet.id), photos.deletedPhotos)
        assertTrue(viewModel.uiState.value.saved)
        assertNull(viewModel.uiState.value.errorMessage)
      }

  @Test
  fun save_whenPhotoDeletionFailsShowsErrorAndRetriesOnTheNextSave() =
      runTest(testDispatcher) {
        val pet = pet().copy(photoUrl = "firestore://photos/pet-1")
        val viewModel = loadedViewModel(pet)
        photos.deleteFailure = IllegalStateException("delete failed")
        viewModel.onPhotoChanged(null)

        viewModel.save()
        advanceUntilIdle()

        assertEquals(R.string.edit_pet_photo_delete_error, viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.saved)
        assertFalse(viewModel.uiState.value.saving)

        photos.deleteFailure = null
        viewModel.save()
        advanceUntilIdle()

        assertEquals(2, photos.deleteCalls)
        assertTrue(viewModel.uiState.value.saved)
        assertNull(localPetRepository.getPet(pet.id).getOrThrow().photoUrl)
      }

  @Test
  fun save_withoutAPhotoNeverDeletesOne() =
      runTest(testDispatcher) {
        val viewModel = loadedViewModel(pet())
        viewModel.onNameChanged("Changed")

        viewModel.save()
        advanceUntilIdle()

        assertEquals(0, photos.deleteCalls)
        assertTrue(viewModel.uiState.value.saved)
      }

  @Test
  fun save_whenPhotoRemovalUpdateFailsKeepsTheOriginalPet() =
      runTest(testDispatcher) {
        val pet = pet().copy(photoUrl = "firestore://photos/pet-1")
        val viewModel = loadedViewModel(pet)
        petRepository.updateFailure = IllegalStateException("update failed")
        viewModel.onPhotoChanged(null)

        viewModel.save()
        advanceUntilIdle()

        assertEquals(pet, localPetRepository.getPet(pet.id).getOrThrow())
        assertEquals(R.string.edit_pet_save_error, viewModel.uiState.value.errorMessage)
        assertEquals(0, photos.deleteCalls)
        assertFalse(viewModel.uiState.value.saved)
        assertFalse(viewModel.uiState.value.saving)
      }

  @Test
  fun save_whenPhotoUploadFailsDoesNotUpdateThePet() =
      runTest(testDispatcher) {
        val pet = pet()
        val viewModel = loadedViewModel(pet)
        photoRepository.failure = IllegalStateException("upload failed")
        viewModel.onNameChanged("Changed")
        viewModel.onPhotoChanged("content://photos/new")

        viewModel.save()
        advanceUntilIdle()

        assertEquals(pet, localPetRepository.getPet(pet.id).getOrThrow())
        assertEquals(0, petRepository.updateCalls)
        assertEquals(R.string.edit_pet_photo_upload_error, viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.saving)
      }

  @Test
  fun save_whenUpdateFailsShowsErrorAndKeepsThePet() =
      runTest(testDispatcher) {
        val pet = pet()
        val viewModel = loadedViewModel(pet)
        petRepository.updateFailure = IllegalStateException("update failed")
        viewModel.onNameChanged("Changed")

        viewModel.save()
        advanceUntilIdle()

        assertEquals(pet, localPetRepository.getPet(pet.id).getOrThrow())
        assertEquals(R.string.edit_pet_save_error, viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.saving)
      }

  @Test
  fun save_withIncompleteRequiredFieldsDoesNothing() =
      runTest(testDispatcher) {
        val pet = pet()
        val viewModel = loadedViewModel(pet)
        viewModel.onNameChanged(" ")

        viewModel.save()
        advanceUntilIdle()

        assertEquals(pet, localPetRepository.getPet(pet.id).getOrThrow())
        assertFalse(viewModel.uiState.value.saving)
        assertEquals(0, petRepository.updateCalls)
      }

  @Test
  fun onPhotoPickerError_showsTheCameraError() =
      runTest(testDispatcher) {
        val viewModel = loadedViewModel(pet())

        viewModel.onPhotoPickerError()

        assertEquals(R.string.photo_camera_unavailable, viewModel.uiState.value.errorMessage)
      }

  @Test
  fun delete_withOpenAlertIsRefused() =
      runTest(testDispatcher) {
        val pet = pet()
        val viewModel = loadedViewModel(pet)
        alertRepository.publishAlert(alert(pet.id))

        viewModel.delete()
        advanceUntilIdle()

        assertEquals(R.string.edit_pet_open_alert_error, viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.deleted)
        assertEquals(pet, localPetRepository.getPet(pet.id).getOrThrow())
        assertEquals(0, petRepository.deleteCalls)
      }

  @Test
  fun delete_whenAlertLookupFailsShowsErrorAndKeepsThePet() =
      runTest(testDispatcher) {
        val pet = pet()
        val viewModel = loadedViewModel(pet)
        alertRepository.failure = IllegalStateException("alert lookup failed")

        viewModel.delete()
        advanceUntilIdle()

        assertEquals(R.string.edit_pet_delete_error, viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.deleted)
        assertEquals(pet, localPetRepository.getPet(pet.id).getOrThrow())
        assertEquals(0, petRepository.deleteCalls)
      }

  @Test
  fun delete_withoutOpenAlertDeletesPetAndSetsDeleted() =
      runTest(testDispatcher) {
        val pet = pet()
        val viewModel = loadedViewModel(pet)
        alertRepository.publishAlert(alert("another-pet"))
        alertRepository.publishAlert(
            alert(pet.id).copy(status = AlertStatus.CLOSED, closedAtMillis = 2L)
        )

        viewModel.delete()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.deleted)
        assertFalse(viewModel.uiState.value.saving)
        assertTrue(localPetRepository.getPet(pet.id).isFailure)
      }

  @Test
  fun delete_whenRepositoryFailsShowsErrorAndKeepsThePet() =
      runTest(testDispatcher) {
        val pet = pet()
        val viewModel = loadedViewModel(pet)
        petRepository.deleteFailure = IllegalStateException("delete failed")

        viewModel.delete()
        advanceUntilIdle()

        assertEquals(R.string.edit_pet_delete_error, viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.deleted)
        assertFalse(viewModel.uiState.value.saving)
        assertEquals(pet, localPetRepository.getPet(pet.id).getOrThrow())
      }

  private suspend fun TestScope.loadedViewModel(pet: Pet): EditPetViewModel {
    localPetRepository.addPet(pet).getOrThrow()
    val viewModel = EditPetViewModel(pet.id, petRepository, alertRepository, photos)
    advanceUntilIdle()
    return viewModel
  }

  private fun pet() =
      Pet(
          id = "pet-1",
          ownerId = "owner-1",
          name = "Buddy",
          species = Species.DOG,
      )

  private fun alert(petId: String) =
      Alert(
          id = "alert-$petId",
          petId = petId,
          ownerId = "owner-1",
          lastKnownLocation = Location(0.0, 0.0),
          lostAtMillis = 1L,
          petName = "Buddy",
          petSpecies = Species.DOG,
          status = AlertStatus.OPEN,
      )

  /** Records the pet photo deletions, which can be made to fail with [deleteFailure]. */
  private class RecordingPhotoRepository(private val delegate: PhotoRepository) :
      PhotoRepository by delegate {
    var deleteFailure: Throwable? = null
    val deletedPhotos = mutableListOf<Pair<String, String>>()
    var deleteCalls = 0

    override suspend fun deletePetPhoto(ownerId: String, petId: String): Result<Unit> {
      deleteCalls++
      deleteFailure?.let {
        return Result.failure(it)
      }
      deletedPhotos += ownerId to petId
      return delegate.deletePetPhoto(ownerId, petId)
    }
  }

  private class ConfigurablePetRepository(private val delegate: PetRepositoryLocal) :
      PetRepository by delegate {
    var updateFailure: Throwable? = null
    var deleteFailure: Throwable? = null
    var updateCalls = 0
    var deleteCalls = 0

    override suspend fun updatePet(pet: Pet): Result<Unit> {
      updateCalls++
      return updateFailure?.let { Result.failure(it) } ?: delegate.updatePet(pet)
    }

    override suspend fun deletePet(id: String): Result<Unit> {
      deleteCalls++
      return deleteFailure?.let { Result.failure(it) } ?: delegate.deletePet(id)
    }
  }
}
