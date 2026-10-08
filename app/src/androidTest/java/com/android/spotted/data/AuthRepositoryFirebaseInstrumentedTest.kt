package com.android.spotted.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.data.auth.AuthRepositoryFirebase
import com.android.spotted.model.auth.User
import com.android.spotted.utils.FakeJwtGenerator
import com.android.spotted.utils.FirebaseEmulators
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
    FirebaseEmulators.configure()
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

    // Wait for the auth state listener to update the StateFlow
    var flowUser: User? = null
    for (i in 1..20) {
      flowUser = authRepository.currentUser.value
      if (flowUser != null) break
      kotlinx.coroutines.delay(100)
    }

    assertNotNull("StateFlow user should not be null after sign-in", flowUser)
    assertEquals("StateFlow email should match", fakeEmail, flowUser?.email)
    assertEquals("StateFlow uid should match returned uid", returnedUser?.uid, flowUser?.uid)
  }

  @Test
  fun signOut_clearsCurrentUserState() = runTest {
    val fakeToken = FakeJwtGenerator.createFakeGoogleIdToken(email = "logout_test@epfl.ch")
    authRepository.signInWithGoogle(fakeToken)
    assertNotNull("User should be signed in initially", authRepository.currentUser.value)

    val result = authRepository.signOut()

    assertTrue("Sign-out should be successful", result.isSuccess)

    // Wait for the auth state listener to update the StateFlow
    // Firebase auth state changes are asynchronous, so we wait for the value to become null
    var isNull = false
    for (i in 1..20) {
      if (authRepository.currentUser.value == null) {
        isNull = true
        break
      }
      kotlinx.coroutines.delay(100L) // Wait 100ms
    }

    assertTrue("StateFlow user should be null after sign-out", isNull)
  }
}
