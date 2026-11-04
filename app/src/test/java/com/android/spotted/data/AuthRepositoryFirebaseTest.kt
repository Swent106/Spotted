// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data

import com.android.spotted.data.auth.AuthRepositoryFirebase
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class AuthRepositoryFirebaseTest {

  private lateinit var mockFirebaseAuth: FirebaseAuth
  private lateinit var mockFirebaseUser: FirebaseUser
  private lateinit var authRepositoryFirebase: AuthRepositoryFirebase

  @Before
  fun setUp() {
    mockFirebaseAuth = mock(FirebaseAuth::class.java)
    mockFirebaseUser = mock(FirebaseUser::class.java)

    // By default, no user is logged in
    `when`(mockFirebaseAuth.currentUser).thenReturn(null)

    // We have to mock the addAuthStateListener to prevent it from crashing the test
    // since we don't have a real Firebase environment.
    doNothing().`when`(mockFirebaseAuth).addAuthStateListener(any())

    authRepositoryFirebase = AuthRepositoryFirebase(mockFirebaseAuth)
  }

  @Test
  fun `initial state sets currentUser to null when no firebase user is logged in`() = runTest {
    val initialUser = authRepositoryFirebase.currentUser.first()
    assertNull(initialUser)
  }

  @Test
  fun `initial state sets currentUser when a firebase user is already logged in`() = runTest {
    `when`(mockFirebaseUser.uid).thenReturn("test_uid_123")
    `when`(mockFirebaseUser.email).thenReturn("test@epfl.ch")
    `when`(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)

    val newRepo = AuthRepositoryFirebase(mockFirebaseAuth)

    val initialUser = newRepo.currentUser.first()
    assertEquals("test_uid_123", initialUser?.uid)
    assertEquals("test@epfl.ch", initialUser?.email)
  }

  @Test
  fun `signInWithGoogle failure returns Result failure`() = runTest {
    val exception = Exception("Network error")
    val failedTask: Task<AuthResult> = Tasks.forException(exception)

    // Mock that when we pass ANY credential, it returns the failed task
    `when`(mockFirebaseAuth.signInWithCredential(any(AuthCredential::class.java)))
        .thenReturn(failedTask)

    val result = authRepositoryFirebase.signInWithGoogle("some_fake_id_token")

    assertTrue(result.isFailure)
    assertEquals(exception.message, result.exceptionOrNull()?.message)
  }

  @Test
  fun `signOut calls firebaseAuth signOut and returns Result success`() = runTest {
    val result = authRepositoryFirebase.signOut()

    assertTrue(result.isSuccess)
    verify(mockFirebaseAuth).signOut()
  }

  @Test
  fun `signOut catches exceptions and returns Result failure`() = runTest {
    val exception = RuntimeException("Sign out failed")
    doThrow(exception).`when`(mockFirebaseAuth).signOut()

    val result = authRepositoryFirebase.signOut()

    assertTrue(result.isFailure)
    assertEquals(exception.message, result.exceptionOrNull()?.message)
  }

  @Test
  fun `signInWithGoogle success returns the signed-in user`() = runTest {
    `when`(mockFirebaseUser.uid).thenReturn("uid_42")
    `when`(mockFirebaseUser.email).thenReturn("pet@owner.ch")
    signInReturns(mockFirebaseUser)

    val result = authRepositoryFirebase.signInWithGoogle("token")

    assertEquals("uid_42", result.getOrNull()?.uid)
    assertEquals("pet@owner.ch", result.getOrNull()?.email)
  }

  @Test
  fun `signInWithGoogle uses a placeholder when the user has no email`() = runTest {
    `when`(mockFirebaseUser.uid).thenReturn("uid_42")
    `when`(mockFirebaseUser.email).thenReturn(null)
    signInReturns(mockFirebaseUser)

    val result = authRepositoryFirebase.signInWithGoogle("token")

    assertEquals("No email provided", result.getOrNull()?.email)
  }

  @Test
  fun `signInWithGoogle fails when Firebase returns no user`() = runTest {
    signInReturns(null)

    val result = authRepositoryFirebase.signInWithGoogle("token")

    assertTrue(result.isFailure)
  }

  @Test
  fun `signInWithGoogle rethrows cancellation instead of returning a failure`() = runTest {
    `when`(mockFirebaseAuth.signInWithCredential(any(AuthCredential::class.java)))
        .thenReturn(Tasks.forCanceled())

    try {
      authRepositoryFirebase.signInWithGoogle("token")
      fail("CancellationException expected")
    } catch (e: CancellationException) {
      // Expected: the caller's coroutine must see the cancellation
    }
  }

  @Test
  fun `signOut rethrows cancellation instead of returning a failure`() = runTest {
    doThrow(CancellationException("cancelled")).`when`(mockFirebaseAuth).signOut()

    try {
      authRepositoryFirebase.signOut()
      fail("CancellationException expected")
    } catch (e: CancellationException) {
      // Expected
    }
  }

  @Test
  fun `auth state changes update currentUser`() = runTest {
    val captor = ArgumentCaptor.forClass(FirebaseAuth.AuthStateListener::class.java)
    verify(mockFirebaseAuth).addAuthStateListener(captor.capture())
    `when`(mockFirebaseUser.uid).thenReturn("uid_42")
    `when`(mockFirebaseUser.email).thenReturn("pet@owner.ch")

    `when`(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
    captor.value.onAuthStateChanged(mockFirebaseAuth)
    assertEquals("uid_42", authRepositoryFirebase.currentUser.value?.uid)

    `when`(mockFirebaseAuth.currentUser).thenReturn(null)
    captor.value.onAuthStateChanged(mockFirebaseAuth)
    assertNull(authRepositoryFirebase.currentUser.value)
  }

  @Test
  fun `deleteAccount fails when nobody is signed in`() = runTest {
    val result = authRepositoryFirebase.deleteAccount()

    assertTrue(result.exceptionOrNull() is IllegalStateException)
  }

  @Test
  fun `deleteAccount deletes the signed-in user`() = runTest {
    `when`(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
    `when`(mockFirebaseUser.delete()).thenReturn(Tasks.forResult(null))

    val result = authRepositoryFirebase.deleteAccount()

    assertTrue(result.isSuccess)
    verify(mockFirebaseUser).delete()
  }

  @Test
  fun `deleteAccount returns the Firebase error as a failure`() = runTest {
    val exception = Exception("Requires recent login")
    `when`(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
    `when`(mockFirebaseUser.delete()).thenReturn(Tasks.forException(exception))

    val result = authRepositoryFirebase.deleteAccount()

    assertEquals(exception.message, result.exceptionOrNull()?.message)
  }

  @Test
  fun `deleteAccount rethrows cancellation instead of returning a failure`() = runTest {
    `when`(mockFirebaseAuth.currentUser).thenReturn(mockFirebaseUser)
    `when`(mockFirebaseUser.delete()).thenReturn(Tasks.forCanceled())

    try {
      authRepositoryFirebase.deleteAccount()
      fail("CancellationException expected")
    } catch (e: CancellationException) {
      // Expected
    }
  }

  /** Makes Firebase sign-in succeed with [user] (null: Firebase returns no user). */
  private fun signInReturns(user: FirebaseUser?) {
    val authResult = mock(AuthResult::class.java)
    `when`(authResult.user).thenReturn(user)
    `when`(mockFirebaseAuth.signInWithCredential(any(AuthCredential::class.java)))
        .thenReturn(Tasks.forResult(authResult))
  }
}
