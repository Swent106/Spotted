package com.android.spotted.ui.petprofile

import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepository
import com.android.spotted.model.Pet.PetRepositoryLocal
import com.android.spotted.model.Pet.Species
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Pet repository whose [getPet] always returns [result]. */
private class StubPetRepository(private val result: Result<Pet>) :
    PetRepository by PetRepositoryLocal() {
  override suspend fun getPet(id: String): Result<Pet> = result
}

@OptIn(ExperimentalCoroutinesApi::class)
class PetProfileViewModelTest {
  private val testDispatcher = StandardTestDispatcher()
  private val rex =
      Pet("pet-1", "owner-1", "Rex", Species.CAT, photoUrl = "https://example.com/rex.jpg")

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun initialState_isLoadingWithoutPet() {
    val state = PetProfileViewModel(PetRepositoryLocal()).uiState.value

    assertTrue(state.isLoading)
    assertNull(state.pet)
    assertNull(state.errorMessage)
  }

  @Test
  fun loadPet_existingPet_exposesPet() = runTest {
    val repository = PetRepositoryLocal().apply { addPet(rex) }
    val viewModel = PetProfileViewModel(repository)

    viewModel.loadPet("pet-1")
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(rex, state.pet)
    assertFalse(state.isLoading)
    assertNull(state.errorMessage)
  }

  @Test
  fun loadPet_unknownPet_stopsLoadingAndShowsError() = runTest {
    val viewModel = PetProfileViewModel(PetRepositoryLocal())

    viewModel.loadPet("missing")
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertNull(state.pet)
    assertFalse(state.isLoading)
    assertEquals("Pet with id missing was not found", state.errorMessage)
  }

  @Test
  fun loadPet_failureWithoutMessage_usesDefaultError() = runTest {
    val viewModel = PetProfileViewModel(StubPetRepository(Result.failure(RuntimeException())))

    viewModel.loadPet("pet-1")
    advanceUntilIdle()

    assertEquals("Unable to load pet", viewModel.uiState.value.errorMessage)
  }

  @Test
  fun loadPet_afterFailure_clearsErrorOnSuccess() = runTest {
    val repository = PetRepositoryLocal()
    val viewModel = PetProfileViewModel(repository)
    viewModel.loadPet("pet-1")
    advanceUntilIdle()

    repository.addPet(rex)
    viewModel.loadPet("pet-1")
    assertTrue(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.errorMessage)
    advanceUntilIdle()

    assertEquals(rex, viewModel.uiState.value.pet)
    assertNull(viewModel.uiState.value.errorMessage)
  }
}
