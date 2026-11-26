// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.MutableCreationExtras
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepositoryLocal
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.AuthRepositoryProvider
import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.model.auth.User
import com.android.spotted.model.fakes.FakeAlertRepository
import com.android.spotted.model.user.FakeUserRepository
import com.android.spotted.model.user.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

  private lateinit var authRepository: ControllableAuthRepository
  private lateinit var user: User
  private val userRepository = FakeUserRepository()
  private val petRepository = PetRepositoryLocal()
  private val alertRepository = FakeAlertRepository()

  @Before
  fun setUp() = runTest {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    authRepository = ControllableAuthRepository()
    user = authRepository.signInWithGoogle("token").getOrThrow()
    userRepository.saveUser(
        UserProfile(uid = user.uid, name = "Rayen", email = "rayen@example.com", username = "rayen")
    )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /**
   * [FakeAuthRepository] whose sign-out can fail with [signOutFailure] or wait for [signOutGate],
   * and whose account deletion can fail with [deleteAccountFailure] or wait for
   * [deleteAccountGate].
   */
  private class ControllableAuthRepository(
      private val fake: FakeAuthRepository = FakeAuthRepository()
  ) : AuthRepository by fake {
    var signOutFailure: Throwable? = null
    var signOutGate: CompletableDeferred<Unit>? = null
    var signOutCalls = 0

    override suspend fun signOut(): Result<Unit> {
      signOutCalls++
      signOutGate?.await()
      signOutFailure?.let {
        return Result.failure(it)
      }
      return fake.signOut()
    }

    var deleteAccountFailure: Throwable? = null
    var deleteAccountGate: CompletableDeferred<Unit>? = null
    var deleteAccountCalls = 0

    override suspend fun deleteAccount(): Result<Unit> {
      deleteAccountCalls++
      deleteAccountGate?.await()
      deleteAccountFailure?.let {
        return Result.failure(it)
      }
      return fake.deleteAccount()
    }
  }

  private fun viewModel() =
      SettingsViewModel(authRepository, userRepository, petRepository, alertRepository)

  // ===================== Initial state =====================

  @Test
  fun initialState_isNotLeaving_andHasNoError() {
    assertEquals(SettingsUiState(isLeaving = false, errorMessage = null), viewModel().uiState.value)
  }

  // ===================== Sign out: success =====================

  @Test
  fun signOut_success_clearsTheSignedInUser_withoutError() {
    val viewModel = viewModel()

    viewModel.signOut()

    assertNull(authRepository.currentUser.value)
    assertNull(viewModel.uiState.value.errorMessage)
    assertEquals(1, authRepository.signOutCalls)
  }

  // ===================== Sign out: failure =====================

  @Test
  fun signOut_failure_keepsTheUserSignedIn_andShowsAnError() {
    authRepository.signOutFailure = Exception("offline")
    val viewModel = viewModel()

    viewModel.signOut()

    assertEquals(user, authRepository.currentUser.value)
    assertEquals(SettingsViewModel.SIGN_OUT_FAILED, viewModel.uiState.value.errorMessage)
  }

  @Test
  fun signOut_failure_reenablesTheButtonForARetry() {
    authRepository.signOutFailure = Exception("offline")
    val viewModel = viewModel()

    viewModel.signOut()

    assertFalse(viewModel.uiState.value.isLeaving)
  }

  @Test
  fun signOut_retryAfterAFailure_clearsTheErrorAndSignsOut() {
    authRepository.signOutFailure = Exception("offline")
    val viewModel = viewModel()
    viewModel.signOut()
    authRepository.signOutFailure = null

    viewModel.signOut()

    assertNull(authRepository.currentUser.value)
    assertNull(viewModel.uiState.value.errorMessage)
    assertEquals(2, authRepository.signOutCalls)
  }

  // ===================== Sign out: in progress =====================

  @Test
  fun signOut_whileInProgress_isLeaving() {
    authRepository.signOutGate = CompletableDeferred()
    val viewModel = viewModel()

    viewModel.signOut()

    assertTrue(viewModel.uiState.value.isLeaving)
    assertEquals(user, authRepository.currentUser.value)
  }

  @Test
  fun signOut_whileInProgress_clearsThePreviousError() {
    authRepository.signOutFailure = Exception("offline")
    val viewModel = viewModel()
    viewModel.signOut()
    authRepository.signOutFailure = null
    authRepository.signOutGate = CompletableDeferred()

    viewModel.signOut()

    assertTrue(viewModel.uiState.value.isLeaving)
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun signOut_tappedTwiceWhileInProgress_signsOutOnce() {
    val gate = CompletableDeferred<Unit>()
    authRepository.signOutGate = gate
    val viewModel = viewModel()

    viewModel.signOut()
    viewModel.signOut()
    gate.complete(Unit)

    assertEquals(1, authRepository.signOutCalls)
    assertNull(authRepository.currentUser.value)
  }

  // ===================== Delete account: confirmation =====================

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
  fun cancelDeleteAccount_hidesTheConfirmationAndDeletesNothing() = runTest {
    addRexAndHisAlert()
    val viewModel = viewModel()
    viewModel.requestDeleteAccount()

    viewModel.cancelDeleteAccount()

    assertFalse(viewModel.uiState.value.isDeleteConfirmationVisible)
    assertNothingDeleted()
  }

  @Test
  fun requestDeleteAccount_whileDeletingTheAccount_showsNoConfirmation() {
    authRepository.deleteAccountGate = CompletableDeferred()
    val viewModel = viewModel()
    viewModel.deleteAccount()

    viewModel.requestDeleteAccount()

    assertTrue(viewModel.uiState.value.isLeaving)
    assertFalse(viewModel.uiState.value.isDeleteConfirmationVisible)
  }

  @Test
  fun requestDeleteAccount_whileSigningOut_showsNoConfirmation() {
    authRepository.signOutGate = CompletableDeferred()
    val viewModel = viewModel()
    viewModel.signOut()

    viewModel.requestDeleteAccount()

    assertFalse(viewModel.uiState.value.isDeleteConfirmationVisible)
  }

  // ===================== Delete account: success =====================

  @Test
  fun deleteAccount_deletesTheProfileAndTheAccount() = runTest {
    viewModel().deleteAccount()

    assertNull(authRepository.currentUser.value)
    assertTrue(userRepository.getUser(user.uid).isFailure)
    assertEquals(1, authRepository.deleteAccountCalls)
  }

  @Test
  fun deleteAccount_alsoDeletesThePetsAndAlerts_soTheNextProfileStartsEmpty() = runTest {
    addRexAndHisAlert()
    val someoneElses = Pet(id = "milo", ownerId = "other", name = "Milo", species = Species.CAT)
    petRepository.addPet(someoneElses)

    viewModel().deleteAccount()

    assertEquals(emptyList<Pet>(), petRepository.getPetsByOwner(user.uid).getOrThrow())
    assertEquals(listOf(someoneElses), petRepository.getPetsByOwner("other").getOrThrow())
    assertEquals(emptyList<Alert>(), alertRepository.publishedAlerts)
  }

  @Test
  fun deleteAccount_afterTheConfirmation_hidesIt() {
    val viewModel = viewModel()
    viewModel.requestDeleteAccount()

    viewModel.deleteAccount()

    assertFalse(viewModel.uiState.value.isDeleteConfirmationVisible)
    assertNull(authRepository.currentUser.value)
  }

  // ===================== Delete account: failure =====================

  @Test
  fun deleteAccount_whenTheAccountCannotBeDeleted_stillSignsOut() = runTest {
    authRepository.deleteAccountFailure = IllegalStateException("Recent login required")

    viewModel().deleteAccount()

    assertNull(authRepository.currentUser.value)
    assertTrue(userRepository.getUser(user.uid).isFailure)
    assertEquals(1, authRepository.signOutCalls)
  }

  @Test
  fun deleteAccount_whenNeitherDeleteNorSignOutWork_showsTheSignOutError() {
    authRepository.deleteAccountFailure = IllegalStateException("Recent login required")
    authRepository.signOutFailure = Exception("offline")
    val viewModel = viewModel()

    viewModel.deleteAccount()

    assertEquals(SettingsViewModel.SIGN_OUT_FAILED, viewModel.uiState.value.errorMessage)
    assertFalse(viewModel.uiState.value.isLeaving)
  }

  @Test
  fun deleteAccount_whenTheProfileCannotBeDeleted_staysSignedInWithAnError() {
    val viewModel = viewModel()
    viewModel.requestDeleteAccount()
    userRepository.failure = IllegalStateException("offline")

    viewModel.deleteAccount()

    assertEquals(SettingsViewModel.DELETE_FAILED, viewModel.uiState.value.errorMessage)
    assertFalse(viewModel.uiState.value.isDeleteConfirmationVisible)
    // Not leaving any more: the user can try again
    assertFalse(viewModel.uiState.value.isLeaving)
    assertEquals(user, authRepository.currentUser.value)
    assertEquals(0, authRepository.deleteAccountCalls)
  }

  // ===================== Delete account: in progress =====================

  @Test
  fun deleteAccount_tappedTwiceWhileInProgress_deletesOnce() {
    val gate = CompletableDeferred<Unit>()
    authRepository.deleteAccountGate = gate
    val viewModel = viewModel()

    viewModel.deleteAccount()
    viewModel.deleteAccount()
    gate.complete(Unit)

    assertEquals(1, authRepository.deleteAccountCalls)
    assertNull(authRepository.currentUser.value)
  }

  @Test
  fun deleteAccount_whileSigningOut_deletesNothing() = runTest {
    addRexAndHisAlert()
    authRepository.signOutGate = CompletableDeferred()
    val viewModel = viewModel()
    viewModel.signOut()

    viewModel.deleteAccount()

    assertNothingDeleted()
  }

  @Test
  fun signOut_whileDeletingTheAccount_doesNotSignOut() {
    authRepository.deleteAccountGate = CompletableDeferred()
    val viewModel = viewModel()
    viewModel.deleteAccount()

    viewModel.signOut()

    assertEquals(0, authRepository.signOutCalls)
  }

  private suspend fun addRexAndHisAlert() {
    petRepository.addPet(Pet(id = "rex", ownerId = user.uid, name = "Rex", species = Species.DOG))
    alertRepository.publishAlert(
        Alert(
            id = "alert-rex",
            petId = "rex",
            ownerId = user.uid,
            lastKnownLocation = Location(46.52, 6.63),
            lostAtMillis = 0L,
            petName = "Rex",
            petSpecies = Species.DOG,
        )
    )
  }

  /** The account, profile, pet and alert added by [addRexAndHisAlert] are all still there. */
  private suspend fun assertNothingDeleted() {
    assertEquals(user, authRepository.currentUser.value)
    assertTrue(userRepository.getUser(user.uid).isSuccess)
    assertEquals(listOf("rex"), petRepository.getPetsByOwner(user.uid).getOrThrow().map { it.id })
    assertEquals(listOf("alert-rex"), alertRepository.publishedAlerts.map { it.id })
  }

  // ===================== Factory =====================

  @Test
  fun factory_usesTheSharedAuthRepository() {
    // The Firebase singletons would crash in a unit test: the repositories read them when they
    // are created
    val firebaseAuth: FirebaseAuth = mock { on { currentUser } doReturn null }
    val collection = mock<CollectionReference>()
    val firestore = mock<FirebaseFirestore> { on { collection(any()) } doReturn collection }
    mockStatic(FirebaseAuth::class.java).use { firebaseAuthStatic ->
      mockStatic(FirebaseFirestore::class.java).use { firestoreStatic ->
        firebaseAuthStatic
            .`when`<FirebaseAuth> { FirebaseAuth.getInstance() }
            .thenReturn(firebaseAuth)
        firestoreStatic
            .`when`<FirebaseFirestore> { FirebaseFirestore.getInstance() }
            .thenReturn(firestore)
        // The photo repository, used to delete the pets' photos, needs the app
        val extras =
            MutableCreationExtras().apply {
              set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, mock<Application>())
            }

        val created = SettingsViewModel.Factory.create(SettingsViewModel::class.java, extras)

        // The repository is private: read it to check it is the app-wide instance, not a new one
        val field = SettingsViewModel::class.java.getDeclaredField("authRepository")
        field.isAccessible = true
        assertSame(AuthRepositoryProvider.authRepository, field.get(created))
      }
    }
  }
}
