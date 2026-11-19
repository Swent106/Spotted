// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.profile

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepositoryLocal
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.model.fakes.FakeAlertRepository
import com.android.spotted.model.user.FakeUserRepository
import com.android.spotted.model.user.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class HelperProfileViewModelTest {

  private lateinit var userRepository: FakeUserRepository
  private lateinit var authRepository: FakeAuthRepository
  private lateinit var uid: String
  private val petRepository = PetRepositoryLocal()
  private val alertRepository = FakeAlertRepository()

  @Before
  fun setUp() = runTest {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    userRepository = FakeUserRepository()
    authRepository = FakeAuthRepository()
    uid = authRepository.signInWithGoogle("token").getOrThrow().uid
    userRepository.saveUser(
        UserProfile(
            uid = uid,
            name = "Rayen",
            email = "rayen@example.com",
            homeArea = "Lausanne",
            username = "rayen",
        )
    )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** A ViewModel as the screen shows it: loaded once. */
  private fun viewModel(auth: AuthRepository = authRepository) =
      HelperProfileViewModel(userRepository, auth, petRepository, alertRepository).apply { load() }

  @Test
  fun init_loadsTheNameUsernameAndArea() {
    val state = viewModel().uiState.value

    assertEquals("Rayen", state.name)
    assertEquals("rayen", state.username)
    assertEquals("Lausanne", state.homeArea)
    assertEquals("R", state.initial)
    assertFalse(state.isLoading)
  }

  @Test
  fun init_whenTheProfileCannotBeLoaded_showsAnError() {
    userRepository.failure = IllegalStateException("offline")

    assertEquals(HelperProfileViewModel.LOAD_FAILED, viewModel().uiState.value.errorMessage)
  }

  @Test
  fun badges_followThePetsBroughtHome() {
    assertFalse(HelperProfileUiState(petsBroughtHome = 0).hasFirstFind)
    assertTrue(HelperProfileUiState(petsBroughtHome = 1).hasFirstFind)
    assertFalse(HelperProfileUiState(petsBroughtHome = 4).hasFiveConfirmed)
    assertTrue(HelperProfileUiState(petsBroughtHome = 5).hasFiveConfirmed)
  }

  @Test
  fun signOut_keepsTheProfile() = runTest {
    viewModel().signOut()

    assertNull(authRepository.currentUser.value)
    assertTrue(userRepository.getUser(uid).isSuccess)
  }

  @Test
  fun deleteAccount_deletesTheProfileAndSignsOut() = runTest {
    viewModel().deleteAccount()

    assertNull(authRepository.currentUser.value)
    assertTrue(userRepository.getUser(uid).isFailure)
  }

  @Test
  fun deleteAccount_alsoDeletesThePetsAndAlerts_soTheNextProfileStartsEmpty() = runTest {
    val rex = Pet(id = "rex", ownerId = uid, name = "Rex", species = Species.DOG)
    val someoneElses = Pet(id = "milo", ownerId = "other", name = "Milo", species = Species.CAT)
    petRepository.addPet(rex)
    petRepository.addPet(someoneElses)
    alertRepository.publishAlert(
        Alert(
            id = "alert-rex",
            petId = "rex",
            ownerId = uid,
            lastKnownLocation = Location(46.52, 6.63),
            lostAtMillis = 0L,
            petName = "Rex",
            petSpecies = Species.DOG,
        )
    )

    viewModel().deleteAccount()

    assertEquals(emptyList<Pet>(), petRepository.getPetsByOwner(uid).getOrThrow())
    assertEquals(listOf(someoneElses), petRepository.getPetsByOwner("other").getOrThrow())
    assertEquals(emptyList<Alert>(), alertRepository.publishedAlerts)
  }

  @Test
  fun deleteAccount_whenTheAccountCannotBeDeleted_stillSignsOut() = runTest {
    val refusingAuth =
        object : AuthRepository by authRepository {
          override suspend fun deleteAccount() =
              Result.failure<Unit>(IllegalStateException("Recent login required"))
        }

    viewModel(refusingAuth).deleteAccount()

    assertNull(authRepository.currentUser.value)
    assertTrue(userRepository.getUser(uid).isFailure)
  }

  @Test
  fun deleteAccount_whenTheProfileCannotBeDeleted_staysSignedInWithAnError() {
    val viewModel = viewModel()
    userRepository.failure = IllegalStateException("offline")

    viewModel.deleteAccount()

    assertEquals(HelperProfileViewModel.DELETE_FAILED, viewModel.uiState.value.errorMessage)
    assertFalse(viewModel.uiState.value.isLeaving)
    assertEquals(uid, authRepository.currentUser.value?.uid)
  }

  @Test
  fun requestDeleteAccount_showsTheConfirmationAndDeletesNothing() = runTest {
    addRexAndHisAlert()
    val viewModel = viewModel()

    viewModel.requestDeleteAccount()

    assertTrue(viewModel.uiState.value.isDeleteConfirmationVisible)
    assertFalse(viewModel.uiState.value.isLeaving)
    assertNothingDeleted()
  }

  @Test
  fun requestDeleteAccount_whileSigningOut_showsNoConfirmation() {
    val signingOut =
        object : AuthRepository by authRepository {
          override suspend fun signOut(): Result<Unit> = awaitCancellation()
        }
    val viewModel = viewModel(signingOut)
    viewModel.signOut()

    viewModel.requestDeleteAccount()

    assertTrue(viewModel.uiState.value.isLeaving)
    assertFalse(viewModel.uiState.value.isDeleteConfirmationVisible)
  }

  @Test
  fun cancelDeleteAccount_hidesTheConfirmationAndDeletesNothing() = runTest {
    addRexAndHisAlert()
    val viewModel = viewModel()
    viewModel.requestDeleteAccount()

    viewModel.cancelDeleteAccount()

    assertFalse(viewModel.uiState.value.isDeleteConfirmationVisible)
    assertNothingDeleted()
  }

  @Test
  fun deleteAccount_afterTheConfirmation_hidesItAndDeletesEverything() = runTest {
    addRexAndHisAlert()
    val viewModel = viewModel()
    viewModel.requestDeleteAccount()

    viewModel.deleteAccount()

    assertFalse(viewModel.uiState.value.isDeleteConfirmationVisible)
    assertNull(authRepository.currentUser.value)
    assertTrue(userRepository.getUser(uid).isFailure)
    assertEquals(emptyList<Pet>(), petRepository.getPetsByOwner(uid).getOrThrow())
    assertEquals(emptyList<Alert>(), alertRepository.publishedAlerts)
  }

  @Test
  fun deleteAccount_afterTheConfirmation_whenItFails_hidesItAndShowsTheError() {
    val viewModel = viewModel()
    viewModel.requestDeleteAccount()
    userRepository.failure = IllegalStateException("offline")

    viewModel.deleteAccount()

    assertFalse(viewModel.uiState.value.isDeleteConfirmationVisible)
    assertEquals(HelperProfileViewModel.DELETE_FAILED, viewModel.uiState.value.errorMessage)
    // Not leaving any more: the user can try again
    assertFalse(viewModel.uiState.value.isLeaving)
  }

  @Test
  fun signOut_whileTheConfirmationIsShown_hidesIt() {
    val signingOut =
        object : AuthRepository by authRepository {
          override suspend fun signOut(): Result<Unit> = awaitCancellation()
        }
    val viewModel = viewModel(signingOut)
    viewModel.requestDeleteAccount()

    viewModel.signOut()

    assertTrue(viewModel.uiState.value.isLeaving)
    assertFalse(viewModel.uiState.value.isDeleteConfirmationVisible)
  }

  private suspend fun addRexAndHisAlert() {
    petRepository.addPet(Pet(id = "rex", ownerId = uid, name = "Rex", species = Species.DOG))
    alertRepository.publishAlert(
        Alert(
            id = "alert-rex",
            petId = "rex",
            ownerId = uid,
            lastKnownLocation = Location(46.52, 6.63),
            lostAtMillis = 0L,
            petName = "Rex",
            petSpecies = Species.DOG,
        )
    )
  }

  /** The account, profile, pet and alert added by [addRexAndHisAlert] are all still there. */
  private suspend fun assertNothingDeleted() {
    assertEquals(uid, authRepository.currentUser.value?.uid)
    assertTrue(userRepository.getUser(uid).isSuccess)
    assertEquals(listOf("rex"), petRepository.getPetsByOwner(uid).getOrThrow().map { it.id })
    assertEquals(listOf("alert-rex"), alertRepository.publishedAlerts.map { it.id })
  }
}
