// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.home

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepository
import com.android.spotted.model.Pet.PetRepositoryLocal
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertStatus
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.User
import com.android.spotted.model.fakes.FakeAddressRepository
import com.android.spotted.model.fakes.FakeAlertRepository
import com.android.spotted.model.user.FakeUserRepository
import com.android.spotted.model.user.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
 * Covers the list, empty and error states of each section, their independence, the profile header,
 * switching user, signing out and [HomeViewModel.refresh].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var authRepository: SwitchableAuthRepository
  private lateinit var userRepository: FakeUserRepository
  private lateinit var petRepository: ControllablePetRepository
  private lateinit var alertRepository: FakeAlertRepository

  @Before
  fun setUp() {
    // viewModelScope runs on Dispatchers.Main, which does not exist in unit tests
    Dispatchers.setMain(testDispatcher)
    authRepository = SwitchableAuthRepository()
    userRepository = FakeUserRepository()
    petRepository = ControllablePetRepository()
    alertRepository = FakeAlertRepository()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // ===================== Test doubles =====================

  /** [AuthRepository] whose signed-in user is set directly by the test. */
  private class SwitchableAuthRepository : AuthRepository {
    override val currentUser = MutableStateFlow<User?>(null)

    /** When set, [signOut] fails with it and the user stays signed in. */
    var signOutFailure: Throwable? = null

    override suspend fun signInWithGoogle(idToken: String): Result<User> =
        Result.failure(UnsupportedOperationException())

    override suspend fun signOut(): Result<Unit> {
      signOutFailure?.let {
        return Result.failure(it)
      }
      currentUser.value = null
      return Result.success(Unit)
    }
  }

  /** [PetRepositoryLocal] that can fail, and can answer slowly for some owners. */
  private class ControllablePetRepository(
      private val local: PetRepositoryLocal = PetRepositoryLocal()
  ) : PetRepository by local {
    var failure: Throwable? = null
    val slowOwners = mutableSetOf<String>()

    override suspend fun getPetsByOwner(ownerId: String): Result<List<Pet>> {
      if (ownerId in slowOwners) delay(SLOW_MILLIS)
      return failure?.let { Result.failure(it) } ?: local.getPetsByOwner(ownerId)
    }
  }

  // ===================== Helpers =====================

  private fun signIn(user: User) {
    authRepository.currentUser.value = user
  }

  private fun addPets(vararg pets: Pet) = runBlocking { pets.forEach { petRepository.addPet(it) } }

  private fun publish(vararg alerts: Alert) = runBlocking {
    alerts.forEach { alertRepository.publishAlert(it) }
  }

  private fun saveProfile(profile: UserProfile) = runBlocking { userRepository.saveUser(profile) }

  private fun viewModel() =
      HomeViewModel(
          authRepository = authRepository,
          userRepository = userRepository,
          petRepository = petRepository,
          alertRepository = alertRepository,
          searchCenter = CENTER,
          radiusKm = RADIUS_KM,
          now = { NOW },
      )

  // ===================== Your pets =====================

  @Test
  fun pets_list_onlyTheSignedInUsersPets() =
      runTest(testDispatcher) {
        signIn(ALICE)
        addPets(LUNA, REX)

        val viewModel = viewModel()
        advanceUntilIdle()

        val pets = viewModel.uiState.value.pets
        assertEquals(listOf(LUNA), pets.items)
        assertFalse(pets.isLoading)
        assertNull(pets.errorMessage)
        assertFalse(pets.isEmpty)
      }

  @Test
  fun pets_empty_whenTheUserHasNoPet() =
      runTest(testDispatcher) {
        signIn(ALICE)

        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.pets.isEmpty)
      }

  @Test
  fun pets_error_whenLoadingFails() =
      runTest(testDispatcher) {
        signIn(ALICE)
        petRepository.failure = Exception("offline")

        val viewModel = viewModel()
        advanceUntilIdle()

        val pets = viewModel.uiState.value.pets
        assertEquals(HomeViewModel.PETS_LOAD_FAILED, pets.errorMessage)
        assertFalse(pets.isLoading)
        assertFalse(pets.isEmpty)
      }

  // ===================== Lost near you =====================

  @Test
  fun lostPets_list_openAlertsInTheRadius_newestFirst_withoutTheUsersOwn() =
      runTest(testDispatcher) {
        signIn(ALICE)
        val older = alert("older", lostAtMillis = 1_000)
        val newer = alert("newer", lostAtMillis = 2_000)
        publish(
            older,
            newer,
            alert("far", location = FAR_AWAY),
            alert("closed", status = AlertStatus.CLOSED),
            alert("mine", ownerId = ALICE.uid),
        )

        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(newer, older), state.lostPetsNearby.items)
        assertNull(state.lostPetsNearby.errorMessage)
        assertEquals(NOW, state.nowMillis)
      }

  @Test
  fun lostPets_empty_whenNoAlertIsNearby() =
      runTest(testDispatcher) {
        signIn(ALICE)
        publish(alert("far", location = FAR_AWAY))

        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.lostPetsNearby.isEmpty)
      }

  @Test
  fun lostPets_error_whenLoadingFails() =
      runTest(testDispatcher) {
        signIn(ALICE)
        alertRepository.failure = Exception("offline")

        val viewModel = viewModel()
        advanceUntilIdle()

        val lostPets = viewModel.uiState.value.lostPetsNearby
        assertEquals(HomeViewModel.LOST_PETS_LOAD_FAILED, lostPets.errorMessage)
        assertFalse(lostPets.isEmpty)
      }

  // ===================== Independent sections =====================

  @Test
  fun alertsFailing_doesNotHideThePets() =
      runTest(testDispatcher) {
        signIn(ALICE)
        addPets(LUNA)
        alertRepository.failure = Exception("offline")

        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(LUNA), state.pets.items)
        assertNull(state.pets.errorMessage)
        assertEquals(HomeViewModel.LOST_PETS_LOAD_FAILED, state.lostPetsNearby.errorMessage)
      }

  @Test
  fun petsFailing_doesNotHideTheLostPets() =
      runTest(testDispatcher) {
        signIn(ALICE)
        val lost = alert("lost")
        publish(lost)
        petRepository.failure = Exception("offline")

        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(lost), state.lostPetsNearby.items)
        assertNull(state.lostPetsNearby.errorMessage)
        assertEquals(HomeViewModel.PETS_LOAD_FAILED, state.pets.errorMessage)
      }

  @Test
  fun slowPets_doNotDelayTheLostPets() =
      runTest(testDispatcher) {
        signIn(ALICE)
        addPets(LUNA)
        val lost = alert("lost")
        publish(lost)
        petRepository.slowOwners += ALICE.uid

        val viewModel = viewModel()
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(listOf(lost), state.lostPetsNearby.items)
        assertTrue(state.pets.isLoading)
      }

  // ===================== Header =====================

  @Test
  fun profile_givesTheNameAndHomeArea() =
      runTest(testDispatcher) {
        signIn(ALICE)
        saveProfile(ALICE_PROFILE)

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals("Alice", viewModel.uiState.value.userName)
        assertEquals("Plainpalais, Geneva", viewModel.uiState.value.homeArea)
      }

  @Test
  fun profileFailing_leavesTheHeaderEmpty_andStillLoadsTheSections() =
      runTest(testDispatcher) {
        signIn(ALICE)
        addPets(LUNA)
        userRepository.failure = Exception("offline")

        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.userName)
        assertEquals(listOf(LUNA), state.pets.items)
        assertNull(state.errorMessage)
      }

  // ===================== Signed-in user =====================

  @Test
  fun notSignedIn_showsErrorAndLoadsNothing() =
      runTest(testDispatcher) {
        addPets(LUNA)
        publish(alert("lost"))

        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(HomeViewModel.NOT_SIGNED_IN, state.errorMessage)
        assertTrue(state.pets.items.isEmpty())
        assertTrue(state.lostPetsNearby.items.isEmpty())
      }

  @Test
  fun signOut_clearsTheUsersData() =
      runTest(testDispatcher) {
        signIn(ALICE)
        saveProfile(ALICE_PROFILE)
        addPets(LUNA)
        val viewModel = viewModel()
        advanceUntilIdle()

        authRepository.signOut()
        advanceUntilIdle()

        assertEquals(
            HomeUiState(errorMessage = HomeViewModel.NOT_SIGNED_IN),
            viewModel.uiState.value,
        )
      }

  @Test
  fun signOut_fromTheViewModel_signsTheUserOutAndClearsTheirData() =
      runTest(testDispatcher) {
        signIn(ALICE)
        addPets(LUNA)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.signOut()
        advanceUntilIdle()

        assertNull(authRepository.currentUser.value)
        assertEquals(
            HomeUiState(errorMessage = HomeViewModel.NOT_SIGNED_IN),
            viewModel.uiState.value,
        )
      }

  @Test
  fun signOut_failure_keepsTheUserAndTheirPets_andShowsAnError() =
      runTest(testDispatcher) {
        signIn(ALICE)
        addPets(LUNA)
        val viewModel = viewModel()
        advanceUntilIdle()
        authRepository.signOutFailure = Exception("offline")

        viewModel.signOut()
        advanceUntilIdle()

        assertEquals(ALICE, authRepository.currentUser.value)
        val state = viewModel.uiState.value
        assertEquals(HomeViewModel.SIGN_OUT_FAILED, state.errorMessage)
        assertEquals(listOf(LUNA), state.pets.items)
      }

  @Test
  fun switchingUser_neverShowsThePreviousUsersData() =
      runTest(testDispatcher) {
        signIn(ALICE)
        saveProfile(ALICE_PROFILE)
        addPets(LUNA, REX)
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(listOf(LUNA), viewModel.uiState.value.pets.items)

        petRepository.slowOwners += BOB.uid
        signIn(BOB)
        runCurrent() // Bob's pets are still loading

        // Right after the switch, nothing of Alice is left
        var state = viewModel.uiState.value
        assertTrue(state.pets.items.isEmpty())
        assertTrue(state.pets.isLoading)
        assertEquals("", state.userName)
        assertEquals("", state.homeArea)

        advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals(listOf(REX), state.pets.items)
      }

  @Test
  fun switchingUser_dropsTheSlowLoadOfThePreviousUser() =
      runTest(testDispatcher) {
        addPets(LUNA, REX)
        petRepository.slowOwners += ALICE.uid
        signIn(ALICE)
        val viewModel = viewModel()
        runCurrent() // Alice's pets are still loading

        signIn(BOB)
        advanceUntilIdle() // Alice's slow load would finish here if it were not cancelled

        assertEquals(listOf(REX), viewModel.uiState.value.pets.items)
      }

  // ===================== Refresh =====================

  @Test
  fun refresh_fetchesTheUpdatedListOfLostPets() =
      runTest(testDispatcher) {
        signIn(ALICE)
        val first = alert("first", lostAtMillis = 1_000)
        publish(first)
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(listOf(first), viewModel.uiState.value.lostPetsNearby.items)

        val second = alert("second", lostAtMillis = 2_000)
        publish(second)
        addPets(LUNA)
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(second, first), state.lostPetsNearby.items)
        assertEquals(listOf(LUNA), state.pets.items)
      }

  @Test
  fun refresh_keepsTheItemsShownWhileLoading() =
      runTest(testDispatcher) {
        signIn(ALICE)
        addPets(LUNA)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.refresh()

        val pets = viewModel.uiState.value.pets
        assertTrue(pets.isLoading)
        assertEquals(listOf(LUNA), pets.items)
      }

  @Test
  fun refresh_failure_keepsTheItemsAndShowsTheError_thenRetrySucceeds() =
      runTest(testDispatcher) {
        signIn(ALICE)
        val first = alert("first")
        publish(first)
        val viewModel = viewModel()
        advanceUntilIdle()

        alertRepository.failure = Exception("offline")
        viewModel.refresh()
        advanceUntilIdle()

        var lostPets = viewModel.uiState.value.lostPetsNearby
        assertEquals(listOf(first), lostPets.items)
        assertEquals(HomeViewModel.LOST_PETS_LOAD_FAILED, lostPets.errorMessage)

        alertRepository.failure = null
        viewModel.refresh()
        advanceUntilIdle()

        lostPets = viewModel.uiState.value.lostPetsNearby
        assertNull(lostPets.errorMessage)
        assertEquals(listOf(first), lostPets.items)
      }

  private companion object {
    const val NOW = 1_700_000_000_000L
    const val SLOW_MILLIS = 1_000L
    const val RADIUS_KM = 3.0
    val CENTER = Location(latitude = 46.2044, longitude = 6.1432)
    val NEARBY = Location(latitude = 46.2050, longitude = 6.1440)
    val FAR_AWAY = Location(latitude = 47.3769, longitude = 8.5417) // Zurich

    val ALICE = User(uid = "alice", email = "alice@example.com")
    val BOB = User(uid = "bob", email = "bob@example.com")
    val ALICE_PROFILE =
        UserProfile(
            uid = ALICE.uid,
            name = "Alice",
            email = ALICE.email,
            homeArea = "Plainpalais, Geneva",
        )
    val LUNA = Pet(id = "luna", ownerId = ALICE.uid, name = "Luna", species = Species.DOG)
    val REX = Pet(id = "rex", ownerId = BOB.uid, name = "Rex", species = Species.DOG)

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

  // ===================== Search around the user's home =====================

  private fun viewModelWithAddresses(addressRepository: FakeAddressRepository) =
      HomeViewModel(
          authRepository = authRepository,
          userRepository = userRepository,
          petRepository = petRepository,
          alertRepository = alertRepository,
          searchCenter = CENTER,
          radiusKm = RADIUS_KM,
          now = { NOW },
          addressRepository = addressRepository,
      )

  @Test
  fun lostPets_areSearchedAroundTheSavedHomePin_withTheSavedRadius() =
      runTest(testDispatcher) {
        signIn(ALICE)
        // 10 km around Zurich: a pet 5 km away is in, one in Geneva is not
        saveProfile(ALICE_PROFILE.copy(homeLocation = FAR_AWAY, alertRadiusKm = 10))
        val inZurich = alert("zurich", location = Location(47.4200, 8.5417))
        val inGeneva = alert("geneva", location = NEARBY)
        publish(inZurich, inGeneva)

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(inZurich), viewModel.uiState.value.lostPetsNearby.items)
        assertEquals(10, viewModel.uiState.value.alertRadiusKm)
      }

  @Test
  fun lostPets_areSearchedAroundTheHomeArea_whenThereIsNoPin() =
      runTest(testDispatcher) {
        signIn(ALICE)
        saveProfile(ALICE_PROFILE)
        val inZurich = alert("zurich", location = FAR_AWAY)
        val inGeneva = alert("geneva", location = NEARBY)
        publish(inZurich, inGeneva)

        val viewModel = viewModelWithAddresses(FakeAddressRepository(location = FAR_AWAY))
        advanceUntilIdle()

        assertEquals(listOf(inZurich), viewModel.uiState.value.lostPetsNearby.items)
      }

  @Test
  fun lostPets_areSearchedAroundTheDefaultCenter_whenTheHomeAreaIsNotFound() =
      runTest(testDispatcher) {
        signIn(ALICE)
        saveProfile(ALICE_PROFILE)
        val inGeneva = alert("geneva", location = NEARBY)
        publish(inGeneva)
        val addressRepository =
            FakeAddressRepository().apply { failure = NoSuchElementException("unknown place") }

        val viewModel = viewModelWithAddresses(addressRepository)
        advanceUntilIdle()

        assertEquals(listOf(inGeneva), viewModel.uiState.value.lostPetsNearby.items)
      }

  @Test
  fun ownLostPets_areKeptForTheMap_evenFarAway_butNotListedAsNearYou() =
      runTest(testDispatcher) {
        signIn(ALICE)
        val mine = alert("mine", ownerId = ALICE.uid, location = FAR_AWAY)
        val theirs = alert("theirs")
        publish(mine, theirs)

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(theirs), viewModel.uiState.value.lostPetsNearby.items)
        assertEquals(listOf(mine), viewModel.uiState.value.ownLostPets)
      }
}
