// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.settings

import androidx.lifecycle.viewmodel.MutableCreationExtras
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.AuthRepositoryProvider
import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.model.auth.User
import com.google.firebase.auth.FirebaseAuth
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

  private lateinit var authRepository: ControllableAuthRepository
  private lateinit var user: User

  @Before
  fun setUp() = runTest {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    authRepository = ControllableAuthRepository()
    user = authRepository.signInWithGoogle("token").getOrThrow()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /**
   * [FakeAuthRepository] whose sign-out can fail with [signOutFailure], or wait for [signOutGate]
   * to finish.
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
  }

  private fun viewModel() = SettingsViewModel(authRepository)

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

  // ===================== Factory =====================

  @Test
  fun factory_usesTheSharedAuthRepository() {
    // FirebaseAuth.getInstance() would crash in a unit test: the shared repository reads it when
    // it is first created
    val firebaseAuth: FirebaseAuth = mock { on { currentUser } doReturn null }
    mockStatic(FirebaseAuth::class.java).use { firebaseAuthStatic ->
      firebaseAuthStatic
          .`when`<FirebaseAuth> { FirebaseAuth.getInstance() }
          .thenReturn(firebaseAuth)

      val created =
          SettingsViewModel.Factory.create(SettingsViewModel::class.java, MutableCreationExtras())

      // The repository is private: read it to check it is the app-wide instance, not a new one
      val field = SettingsViewModel::class.java.getDeclaredField("authRepository")
      field.isAccessible = true
      assertSame(AuthRepositoryProvider.authRepository, field.get(created))
    }
  }
}
