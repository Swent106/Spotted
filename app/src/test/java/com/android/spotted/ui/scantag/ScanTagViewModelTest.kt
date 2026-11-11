// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.scantag

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.FakePetRepository
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepository
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.fakes.FakeAlertRepository
import com.android.spotted.model.fakes.FakePetTagRepository
import com.android.spotted.model.tag.PetTag
import com.android.spotted.model.tag.PetTagRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FailingPetRepository(private val failure: Throwable) :
    PetRepository by FakePetRepository() {
  override suspend fun getPet(id: String): Result<Pet> = Result.failure(failure)
}

private class BlockingPetTagRepository(
    private val blockedTagId: String,
    private val delegate: PetTagRepository,
) : PetTagRepository by delegate {
  var blockedScanCancelled = false
    private set

  override suspend fun getTag(tagId: String): Result<PetTag?> {
    if (tagId == blockedTagId) {
      try {
        awaitCancellation()
      } catch (cancellation: CancellationException) {
        blockedScanCancelled = true
        throw cancellation
      }
    }
    return delegate.getTag(tagId)
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ScanTagViewModelTest {
  private val testDispatcher = StandardTestDispatcher()
  private val pet =
      Pet(
          id = "pet-1",
          ownerId = "owner-1",
          name = "Rex",
          species = Species.DOG,
          photoUrl = "https://example.com/rex.jpg",
          allergies = listOf("Peanuts"),
          behaviors = setOf(Behavior.FEARFUL_OF_STRANGERS),
      )

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun onTagScanned_linkedTag_exposesPetAndOnlyVisibleContactsAndMatchingAlert() = runTest {
    val tagRepository =
        FakePetTagRepository(
            initialTags =
                listOf(
                    tag.copy(showPhone = false, showEmail = true),
                )
        )
    val petRepository = FakePetRepository().apply { addPet(pet) }
    val alertRepository = FakeAlertRepository()
    alertRepository.publishAlert(
        alert(id = "alert-other-pet", petId = "pet-2").copy(lostAtMillis = 2L)
    )
    alertRepository.publishAlert(alert(id = "alert-1", petId = pet.id))
    val viewModel = ScanTagViewModel(tagRepository, petRepository, alertRepository)

    viewModel.onTagScanned(tag.tagId)
    assertEquals(ScanTagStep.Loading, viewModel.uiState.value.step)
    advanceUntilIdle()

    assertEquals(
        ScanTagUiState(
            step = ScanTagStep.Found,
            petName = pet.name,
            photoUrl = pet.photoUrl,
            species = pet.species,
            allergies = pet.allergies,
            behaviors = pet.behaviors,
            visibleEmail = tag.contactEmail,
            openAlertId = "alert-1",
        ),
        viewModel.uiState.value,
    )
  }

  @Test
  fun onTagScanned_linkedTag_exposesPhoneAndHidesEmailWhenConfigured() = runTest {
    val petRepository = FakePetRepository().apply { addPet(pet) }
    val viewModel =
        ScanTagViewModel(
            FakePetTagRepository(
                initialTags = listOf(tag.copy(showPhone = true, showEmail = false))
            ),
            petRepository,
            FakeAlertRepository(),
        )

    viewModel.onTagScanned(tag.tagId)
    advanceUntilIdle()

    assertEquals(ScanTagStep.Found, viewModel.uiState.value.step)
    assertEquals(tag.contactPhone, viewModel.uiState.value.visiblePhone)
    assertNull(viewModel.uiState.value.visibleEmail)
  }

  @Test
  fun onTagScanned_tagNotFound_setsUnknown() = runTest {
    val petRepository = FakePetRepository().apply { addPet(pet) }
    val viewModel =
        ScanTagViewModel(
            FakePetTagRepository(initialTags = listOf(tag)),
            petRepository,
            FakeAlertRepository(),
        )

    viewModel.onTagScanned(tag.tagId)
    assertEquals(ScanTagStep.Loading, viewModel.uiState.value.step)
    advanceUntilIdle()
    assertEquals(ScanTagStep.Found, viewModel.uiState.value.step)
    assertEquals(pet.name, viewModel.uiState.value.petName)

    viewModel.onTagScanned("unknown-tag")
    assertEquals(ScanTagStep.Loading, viewModel.uiState.value.step)
    assertNull(viewModel.uiState.value.petName)
    advanceUntilIdle()

    assertEquals(ScanTagStep.Unknown, viewModel.uiState.value.step)
    assertNull(viewModel.uiState.value.petName)
  }

  @Test
  fun onTagScanned_newScanCancelsPreviousScan() = runTest {
    val secondPet = pet.copy(id = "pet-2", name = "Milo")
    val tagRepository =
        BlockingPetTagRepository(
            blockedTagId = "blocked-tag",
            delegate =
                FakePetTagRepository(
                    initialTags =
                        listOf(
                            tag.copy(tagId = "blocked-tag"),
                            tag.copy(tagId = "next-tag", petId = secondPet.id),
                        )
                ),
        )
    val petRepository =
        FakePetRepository().apply {
          addPet(pet)
          addPet(secondPet)
        }
    val viewModel = ScanTagViewModel(tagRepository, petRepository, FakeAlertRepository())

    viewModel.onTagScanned("blocked-tag")
    runCurrent()
    viewModel.onTagScanned("next-tag")
    advanceUntilIdle()

    assertTrue(tagRepository.blockedScanCancelled)
    assertEquals(ScanTagStep.Found, viewModel.uiState.value.step)
    assertEquals(secondPet.name, viewModel.uiState.value.petName)
  }

  @Test
  fun onTagScanned_deletedPet_setsUnknown() = runTest {
    val viewModel =
        ScanTagViewModel(
            FakePetTagRepository(initialTags = listOf(tag)),
            FakePetRepository(),
            FakeAlertRepository(),
        )

    viewModel.onTagScanned(tag.tagId)
    advanceUntilIdle()

    assertEquals(ScanTagStep.Unknown, viewModel.uiState.value.step)
  }

  @Test
  fun onTagScanned_petWithoutOpenAlert_hasNoAlertId() = runTest {
    val petRepository = FakePetRepository().apply { addPet(pet) }
    val viewModel =
        ScanTagViewModel(
            FakePetTagRepository(initialTags = listOf(tag)),
            petRepository,
            FakeAlertRepository(),
        )

    viewModel.onTagScanned(tag.tagId)
    advanceUntilIdle()

    assertEquals(ScanTagStep.Found, viewModel.uiState.value.step)
    assertNull(viewModel.uiState.value.openAlertId)
  }

  @Test
  fun onTagScanned_tagLookupFailure_setsError() = runTest {
    val failure = IllegalStateException("tag lookup failed")
    val tagRepository = FakePetTagRepository().apply { getTagFailure = failure }
    val viewModel = ScanTagViewModel(tagRepository, FakePetRepository(), FakeAlertRepository())

    viewModel.onTagScanned(tag.tagId)
    advanceUntilIdle()

    assertEquals(ScanTagStep.Error, viewModel.uiState.value.step)
  }

  @Test
  fun onTagScanned_petLookupFailure_setsError() = runTest {
    val failure = IllegalStateException("pet lookup failed")
    val viewModel =
        ScanTagViewModel(
            FakePetTagRepository(initialTags = listOf(tag)),
            FailingPetRepository(failure),
            FakeAlertRepository(),
        )

    viewModel.onTagScanned(tag.tagId)
    advanceUntilIdle()

    assertEquals(ScanTagStep.Error, viewModel.uiState.value.step)
  }

  @Test
  fun onTagScanned_alertLookupFailure_setsErrorWithoutPartialPetData() = runTest {
    val failure = IllegalStateException("alert lookup failed")
    val petRepository = FakePetRepository().apply { addPet(pet) }
    val alertRepository = FakeAlertRepository().apply { this.failure = failure }
    val viewModel =
        ScanTagViewModel(
            FakePetTagRepository(initialTags = listOf(tag)),
            petRepository,
            alertRepository,
        )

    viewModel.onTagScanned(tag.tagId)
    advanceUntilIdle()

    assertEquals(ScanTagStep.Error, viewModel.uiState.value.step)
    assertNull(viewModel.uiState.value.petName)
  }

  private val tag =
      PetTag(
          tagId = "A1B2",
          petId = "pet-1",
          ownerId = "owner-1",
          linkedAtMillis = 1L,
          contactPhone = "+41 79 123 45 67",
          contactEmail = "owner@example.com",
      )

  private fun alert(id: String, petId: String) =
      Alert(
          id = id,
          petId = petId,
          ownerId = pet.ownerId,
          lastKnownLocation = Location(latitude = 0.0, longitude = 0.0),
          lostAtMillis = 1L,
          petName = pet.name,
          petSpecies = pet.species,
      )
}
