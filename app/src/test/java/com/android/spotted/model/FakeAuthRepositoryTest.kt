package com.android.spotted.model

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeAuthRepositoryTest {

  private lateinit var authRepository: FakeAuthRepository
  private val testUser = User(uid = "user_123", email = "test@example.com")

  @Before
  fun setUp() {
    authRepository = FakeAuthRepository()
  }

  @Test
  fun `initial state has null current user`() = runTest {
    // Assert
    val initialUser = authRepository.currentUser.first()
    assertNull("Initially, the current user should be null", initialUser)
  }

  @Test
  fun `signInWithGoogle success updates currentUser and returns success`() = runTest {
    // Arrange
    authRepository.shouldSimulateFailure = false

    // Act
    val result = authRepository.signInWithGoogle(testUser)

    // Assert
    assertTrue("Result should be success", result.isSuccess)
    assertEquals("Result should contain the test user", testUser, result.getOrNull())

    val currentUser = authRepository.currentUser.first()
    assertEquals("currentUser state flow should emit the logged in user", testUser, currentUser)
  }

  @Test
  fun `signInWithGoogle failure does not update currentUser and returns failure`() = runTest {
    // Arrange
    authRepository.shouldSimulateFailure = true

    // Act
    val result = authRepository.signInWithGoogle(testUser)

    // Assert
    assertTrue("Result should be failure", result.isFailure)
    assertTrue(
        "Exception message should match",
        result.exceptionOrNull()?.message?.contains("Simulated authentication failure") == true)

    val currentUser = authRepository.currentUser.first()
    assertNull("currentUser state flow should remain null after failure", currentUser)
  }

  @Test
  fun `signOut clears the currentUser and returns success`() = runTest {
    // Arrange
    authRepository.shouldSimulateFailure = false
    authRepository.signInWithGoogle(testUser) // Sign in first
    var currentUser = authRepository.currentUser.first()
    assertEquals("User should be signed in", testUser, currentUser)

    // Act
    val result = authRepository.signOut()

    // Assert
    assertTrue("Sign out should be successful", result.isSuccess)

    currentUser = authRepository.currentUser.first()
    assertNull("currentUser state flow should be null after sign out", currentUser)
  }
}
