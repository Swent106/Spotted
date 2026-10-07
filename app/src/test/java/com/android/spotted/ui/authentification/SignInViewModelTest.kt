package com.android.spotted.ui.authentification

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.android.spotted.model.auth.FakeAuthRepository
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.credentials.exceptions.NoCredentialException
/**
 * Unit tests for [SignInViewModel].
 *
 * Covers the success and failure branches of the repository call, every catch block of the
 * sign-in flow, the non-Google credential case, the double-click guard and error clearing.
 * Claude helped for this class
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // signIn is annotated with @RequiresApi(34)
class SignInViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeAuthRepository
    private lateinit var viewModel: SignInViewModel

    // FirebaseAuth.getInstance() is mocked in EVERY test (it would crash in a unit test otherwise)
    private lateinit var firebaseAuthStatic: MockedStatic<FirebaseAuth>
    private val fakeFirebaseUser: FirebaseUser = mock()
    private val fakeFirebaseAuth: FirebaseAuth = mock { on { currentUser } doReturn fakeFirebaseUser }

    // Fake Context: returns a fake client id instead of reading google-services.json
    private val context: Context = mock {
        on { getString(any()) } doReturn "fake-client-id"
    }

    @Before
    fun setUp() {
        // viewModelScope runs on Dispatchers.Main, which does not exist in unit tests
        Dispatchers.setMain(testDispatcher)

        firebaseAuthStatic = mockStatic(FirebaseAuth::class.java)
        firebaseAuthStatic.`when`<FirebaseAuth> { FirebaseAuth.getInstance() }
            .thenReturn(fakeFirebaseAuth)

        fakeRepo = FakeAuthRepository()
        viewModel = SignInViewModel(fakeRepo)
    }

    @After
    fun tearDown() {
        firebaseAuthStatic.close()
        Dispatchers.resetMain()
    }

    // ===================== Helpers =====================

    /** Returns a CredentialManager that yields a Google credential containing [token]. */
    private fun credentialManagerReturningGoogleToken(token: String = "fake-token"): CredentialManager {
        val googleCredential = GoogleIdTokenCredential.Builder()
            .setId("test@example.com")
            .setIdToken(token)
            .build()
        return mock {
            onBlocking { getCredential(any<Context>(), any<GetCredentialRequest>()) } doReturn
                    GetCredentialResponse(googleCredential)
        }
    }

    /** Returns a CredentialManager that throws [error] when a credential is requested. */
    private fun credentialManagerThrowing(error: Exception): CredentialManager = mock {
        onBlocking { getCredential(any<Context>(), any<GetCredentialRequest>()) } doAnswer {
            throw error
        }
    }

    // ===================== Initial state =====================

    @Test
    fun initialState_isEmpty() {
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.user)
        assertNull(state.errorMessage)
    }

    // ===================== fold: onSuccess / onFailure =====================

    @Test
    fun onSuccess_setsFirebaseUser_andClearsError() = runTest(testDispatcher) {
        viewModel.signIn(context, credentialManagerReturningGoogleToken())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(fakeFirebaseUser, state.user)
        assertNull(state.errorMessage)
        // The fake repository was called and stored its user
        assertEquals("fake_uid_from_token", fakeRepo.currentUser.value?.uid)
    }

    @Test
    fun onFailure_putsRepositoryErrorInState() = runTest(testDispatcher) {
        fakeRepo.shouldSimulateFailure = true

        viewModel.signIn(context, credentialManagerReturningGoogleToken())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.user)
        assertEquals("Simulated authentication failure in FakeAuthRepository", state.errorMessage)
        assertNull(fakeRepo.currentUser.value)
    }

    // ===================== catch blocks =====================

    @Test
    fun catchCancellation_showsSignInCancelled() = runTest(testDispatcher) {
        viewModel.signIn(context, credentialManagerThrowing(GetCredentialCancellationException()))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.user)
        assertEquals("Sign-in cancelled", state.errorMessage)
        assertNull(fakeRepo.currentUser.value) // The repository is never called
    }

    @Test
    fun catchGetCredentialException_showsFailedToGetCredentials() = runTest(testDispatcher) {
        // NoCredentialException is an androidx GetCredentialException (no Google account on the device)
        viewModel.signIn(context, credentialManagerThrowing(NoCredentialException("No Google account")))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.user)
        assertEquals("Failed to get credentials: No Google account", state.errorMessage)
    }

    @Test
    fun catchGenericException_showsUnexpectedError() = runTest(testDispatcher) {
        viewModel.signIn(context, credentialManagerThrowing(IllegalStateException("Boom")))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.user)
        assertEquals("Unexpected error: Boom", state.errorMessage)
    }

    // ===================== extractGoogleIdToken =====================

    @Test
    fun nonGoogleCredential_throwsAndShowsUnexpectedError() = runTest(testDispatcher) {
        // A password credential is not a Google ID token: extractGoogleIdToken throws
        val cm: CredentialManager = mock {
            onBlocking { getCredential(any<Context>(), any<GetCredentialRequest>()) } doReturn
                    GetCredentialResponse(PasswordCredential("user", "password"))
        }

        viewModel.signIn(context, cm)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.user)
        assertTrue(state.errorMessage!!.startsWith("Unexpected error: Unexpected credential type"))
        assertNull(fakeRepo.currentUser.value) // The repository is never called
    }

    // ===================== isLoading guard =====================

    @Test
    fun secondClickWhileLoading_isIgnored() = runTest(testDispatcher) {
        // The Google account picker stays "open" until this deferred is completed
        val pending = CompletableDeferred<GetCredentialResponse>()
        val cm: CredentialManager = mock {
            onBlocking { getCredential(any<Context>(), any<GetCredentialRequest>()) } doSuspendableAnswer {
                pending.await()
            }
        }

        viewModel.signIn(context, cm)
        runCurrent() // The coroutine starts and suspends on getCredential
        assertTrue(viewModel.uiState.value.isLoading) // The loader is shown

        viewModel.signIn(context, cm) // Second click while loading
        runCurrent()

        // getCredential was called only once
        verifyBlocking(cm, times(1)) { getCredential(any<Context>(), any<GetCredentialRequest>()) }

        // Finish the sign-in
        val googleCredential = GoogleIdTokenCredential.Builder()
            .setId("test@example.com")
            .setIdToken("fake-token")
            .build()
        pending.complete(GetCredentialResponse(googleCredential))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
    }

    // ===================== clearErrorMsg =====================

    @Test
    fun clearErrorMsg_removesError() = runTest(testDispatcher) {
        fakeRepo.shouldSimulateFailure = true
        viewModel.signIn(context, credentialManagerReturningGoogleToken())
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.errorMessage != null)

        viewModel.clearErrorMsg()

        assertNull(viewModel.uiState.value.errorMessage)
    }
}