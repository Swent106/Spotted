// Portions of this code were generated with the help of Copilot.
// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.data.auth.AuthRepositoryFirebase
import com.android.spotted.model.auth.User
import com.android.spotted.utils.FakeJwtGenerator
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthRepositoryFirebaseInstrumentedTest {

  private lateinit var firebaseAuth: FirebaseAuth
  private lateinit var authRepository: AuthRepositoryFirebase

  @Before
  fun setUp() {
    // Get the real FirebaseAuth instance
    firebaseAuth = FirebaseAuth.getInstance()

    // Ensure we start with a clean state (logged out)
    firebaseAuth.signOut()

    authRepository = AuthRepositoryFirebase(firebaseAuth)
  }

  @After
  fun tearDown() {
    firebaseAuth.signOut()
  }

  @Test
  fun initialState_hasNullUser() = runTest {
    val user = authRepository.currentUser.first()
    assertNull("Initial user should be null", user)
  }

  @Test
  fun signInWithGoogle_withValidEmulatedToken_updatesStateFlow() = runTest {
    val fakeEmail = "emulated_user@epfl.ch"
    val fakeToken = FakeJwtGenerator.createFakeGoogleIdToken(email = fakeEmail)

    val result = authRepository.signInWithGoogle(fakeToken)

    // Print the error if it fails so we can debug it
    if (result.isFailure) {
      println("FIREBASE ERROR: ${result.exceptionOrNull()?.message}")
    }

    assertTrue(
        "Sign-in should be successful, but failed with: ${result.exceptionOrNull()?.message}",
        result.isSuccess,
    )

    val returnedUser = result.getOrNull()
    assertNotNull("Returned user should not be null", returnedUser)
    assertEquals("Email should match the fake token", fakeEmail, returnedUser?.email)

    awaitCurrentUser { it != null }
    val flowUser: User? = authRepository.currentUser.value

    assertNotNull("StateFlow user should not be null after sign-in", flowUser)
    assertEquals("StateFlow email should match", fakeEmail, flowUser?.email)
    assertEquals("StateFlow uid should match returned uid", returnedUser?.uid, flowUser?.uid)
  }

  @Test
  fun signOut_clearsCurrentUserState() = runTest {
    val fakeToken = FakeJwtGenerator.createFakeGoogleIdToken(email = "logout_test@epfl.ch")
    val signInResult = authRepository.signInWithGoogle(fakeToken)
    assertTrue("Sign-in should be successful", signInResult.isSuccess)

    awaitCurrentUser { it != null }
    assertNotNull("User should be signed in initially", authRepository.currentUser.value)

    val result = authRepository.signOut()

    assertTrue("Sign-out should be successful", result.isSuccess)
    assertTrue("StateFlow user should be null after sign-out", awaitCurrentUser { it == null })
  }

  /**
   * Waits until [currentUser][AuthRepositoryFirebase.currentUser] matches [condition], and returns
   * whether it did. Firebase calls the auth state listener later, on another thread. `runTest`
   * skips `delay`, so the wait runs on [Dispatchers.Default] to last for real.
   */
  private suspend fun awaitCurrentUser(condition: (User?) -> Boolean): Boolean =
      withContext(Dispatchers.Default) {
        withTimeoutOrNull(TIMEOUT_MILLIS) {
          authRepository.currentUser.first(condition)
          true
        } ?: false
      }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
  }
}
