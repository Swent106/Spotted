package com.android.spotted.ui.home

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepository
import com.android.spotted.model.Pet.PetRepositoryLocal
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertStatus
import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.model.fakes.FakeAlertRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
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

/**
 * Unit tests for [HomeViewModel] with fake repositories and a fixed clock.
 *
 * Covers the list, empty and error states of the pets and of the lost pets nearby, and [refresh].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var authRepository: FakeAuthRepository
  private lateinit var petRepository: SwitchablePetRepository
  private lateinit var alertRepository: FakeAlertRepository

  @Before
  fun setUp() {
    // viewModelScope runs on Dispatchers.Main, which does not exist in unit tests
    Dispatchers.setMain(testDispatcher)
    authRepository = FakeAuthRepository()
    petRepository = SwitchablePetRepository()
    alertRepository = FakeAlertRepository()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // ===================== Helpers =====================

  /** [PetRepositoryLocal] whose reads fail while [failure] is set. */
  private class SwitchablePetRepository(private val local: PetRepositoryLocal = PetRepositoryLocal()) :
      PetRepository by local {
    var failure: Throwable? = null

    override suspend fun getPetsByOwner(ownerId: String): Result<List<Pet>> =
        failure?.let { Result.failure(it) } ?: local.getPetsByOwner(ownerId)
  }

  private fun signIn(): String = runBlocking {
    authRepository.signInWithGoogle("fake-token").getOrThrow().uid
  }

  private fun addPets(vararg pets: Pet) = runBlocking { pets.forEach { petRepository.addPet(it) } }

  private fun publish(vararg alerts: Alert) = runBlocking {
    alerts.forEach { alertRepository.publishAlert(it) }
  }

  private fun viewModel() =
      HomeViewModel(
          authRepository = authRepository,
          petRepository = petRepository,
          alertRepository = alertRepository,
          searchCenter = CENTER,
          radiusKm = RADIUS_KM,
          now = { NOW },
      )

  // ===================== Pets =====================

  @Test
  fun init_loadsOnlyTheSignedInUsersPets() =
      runTest(testDispatcher) {
        val uid = signIn()
        val luna = pet("luna", uid)
        addPets(luna, pet("rex", ownerId = "someone-else"))

        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(luna), state.myPets)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertFalse(state.hasNoPets)
      }

  @Test
  fun init_noPets_showsEmptyState() =
      runTest(testDispatcher) {
        signIn()

        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.myPets.isEmpty())
        assertTrue(state.hasNoPets)
        assertNull(state.errorMessage)
      }

  @Test
  fun init_petsFail_showsErrorAndNoEmptyState() =
      runTest(testDispatcher) {
        signIn()
        petRepository.failure = Exception("offline")

        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(HomeViewModel.LOAD_FAILED, state.errorMessage)
        assertFalse(state.isLoading)
        assertFalse(state.hasNoPets)
      }

  // ===================== Lost pets nearby =====================

  @Test
  fun init_loadsOpenAlertsInTheRadius_newestFirst_withoutTheUsersOwn() =
      runTest(testDispatcher) {
        val uid = signIn()
        val older = alert("older", lostAtMillis = 1_000)
        val newer = alert("newer", lostAtMillis = 2_000)
        val far = alert("far", location = FAR_AWAY)
        val closed = alert("closed", status = AlertStatus.CLOSED)
        val mine = alert("mine", ownerId = uid)
        publish(older, newer, far, closed, mine)

        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(newer, older), state.lostPetsNearby)
        assertEquals(NOW, state.nowMillis)
        assertFalse(state.hasNoLostPetsNearby)
      }

  @Test
  fun init_noAlertsNearby_showsEmptyState() =
      runTest(testDispatcher) {
        signIn()
        publish(alert("far", location = FAR_AWAY))

        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.lostPetsNearby.isEmpty())
        assertTrue(state.hasNoLostPetsNearby)
      }

  @Test
  fun init_alertsFail_showsErrorButStillShowsThePets() =
      runTest(testDispatcher) {
        val uid = signIn()
        val luna = pet("luna", uid)
        addPets(luna)
        alertRepository.failure = Exception("offline")

        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(HomeViewModel.LOAD_FAILED, state.errorMessage)
        assertEquals(listOf(luna), state.myPets)
        assertFalse(state.hasNoLostPetsNearby)
      }

  // ===================== Signed out =====================

  @Test
  fun init_notSignedIn_showsErrorAndLoadsNothing() =
      runTest(testDispatcher) {
        addPets(pet("luna", "fake_uid_from_token"))
        publish(alert("a1"))

        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(HomeViewModel.NOT_SIGNED_IN, state.errorMessage)
        assertTrue(state.myPets.isEmpty())
        assertTrue(state.lostPetsNearby.isEmpty())
        assertFalse(state.isLoading)
      }

  // ===================== Refresh =====================

  @Test
  fun refresh_isLoadingUntilTheDataArrives() =
      runTest(testDispatcher) {
        signIn()
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.refresh()
        assertTrue(viewModel.uiState.value.isLoading)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
      }

  @Test
  fun refresh_fetchesTheUpdatedListOfLostPets() =
      runTest(testDispatcher) {
        val uid = signIn()
        val first = alert("first", lostAtMillis = 1_000)
        publish(first)
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(listOf(first), viewModel.uiState.value.lostPetsNearby)

        val second = alert("second", lostAtMillis = 2_000)
        publish(second)
        val luna = pet("luna", uid)
        addPets(luna)
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(second, first), state.lostPetsNearby)
        assertEquals(listOf(luna), state.myPets)
      }

  @Test
  fun refresh_failure_keepsTheListsAlreadyShown_thenRetrySucceeds() =
      runTest(testDispatcher) {
        val uid = signIn()
        val luna = pet("luna", uid)
        val first = alert("first")
        addPets(luna)
        publish(first)
        val viewModel = viewModel()
        advanceUntilIdle()

        alertRepository.failure = Exception("offline")
        petRepository.failure = Exception("offline")
        viewModel.refresh()
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals(HomeViewModel.LOAD_FAILED, state.errorMessage)
        assertEquals(listOf(luna), state.myPets)
        assertEquals(listOf(first), state.lostPetsNearby)

        alertRepository.failure = null
        petRepository.failure = null
        viewModel.refresh()
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertNull(state.errorMessage)
        assertEquals(listOf(first), state.lostPetsNearby)
      }

  @Test
  fun clearErrorMsg_hidesTheError() =
      runTest(testDispatcher) {
        signIn()
        alertRepository.failure = Exception("offline")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.clearErrorMsg()

        assertNull(viewModel.uiState.value.errorMessage)
      }

  private companion object {
    const val NOW = 1_700_000_000_000L
    const val RADIUS_KM = 3.0
    val CENTER = Location(latitude = 46.2044, longitude = 6.1432)
    val NEARBY = Location(latitude = 46.2050, longitude = 6.1440)
    val FAR_AWAY = Location(latitude = 47.3769, longitude = 8.5417) // Zurich

    fun pet(id: String, ownerId: String) =
        Pet(id = id, ownerId = ownerId, name = id, species = Species.DOG)

    fun alert(
        id: String,
        ownerId: String = "someone-else",
        location: Location = NEARBY,
        lostAtMillis: Long = 1_000,
        status: AlertStatus = AlertStatus.OPEN,
    ) =
        Alert(
            id = id,
            petId = "pet-$id",
            ownerId = ownerId,
            lastKnownLocation = location,
            lostAtMillis = lostAtMillis,
            status = status,
            petName = id,
            petSpecies = Species.CAT,
        )
  }
}
