package com.android.spotted.data

import com.android.spotted.data.auth.AuthRepositoryFirebase
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
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
}
