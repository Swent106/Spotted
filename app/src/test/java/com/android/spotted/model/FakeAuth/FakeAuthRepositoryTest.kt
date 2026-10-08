package com.android.spotted.model.FakeAuth

import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.model.auth.User
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert
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
    Assert.assertNull("Initially, the current user should be null", initialUser)
  }

  @Test
  fun `signInWithGoogle success updates currentUser and returns success`() = runTest {
    // Arrange
    authRepository.shouldSimulateFailure = false
    val fakeToken = "valid_fake_token"

    // Act
    val result = authRepository.signInWithGoogle(fakeToken)

    // Assert
    Assert.assertTrue("Result should be success", result.isSuccess)
    val returnedUser = result.getOrNull()
    Assert.assertEquals("Result uid should match", "fake_uid_from_token", returnedUser?.uid)

    val currentUser = authRepository.currentUser.first()
    Assert.assertEquals(
        "currentUser state flow should emit the logged in user",
        returnedUser,
        currentUser,
    )
  }

  @Test
  fun `signInWithGoogle failure does not update currentUser and returns failure`() = runTest {
    // Arrange
    authRepository.shouldSimulateFailure = true
    val fakeToken = "invalid_fake_token"

    // Act
    val result = authRepository.signInWithGoogle(fakeToken)

    // Assert
    Assert.assertTrue("Result should be failure", result.isFailure)
    Assert.assertTrue(
        "Exception message should match",
        result.exceptionOrNull()?.message?.contains("Simulated authentication failure") == true,
    )

    val currentUser = authRepository.currentUser.first()
    Assert.assertNull("currentUser state flow should remain null after failure", currentUser)
  }

  @Test
  fun `signOut clears the currentUser and returns success`() = runTest {
    // Arrange
    authRepository.shouldSimulateFailure = false
    authRepository.signInWithGoogle("some_token") // Sign in first
    var currentUser = authRepository.currentUser.first()
    Assert.assertEquals("User should be signed in", "fake_uid_from_token", currentUser?.uid)

    // Act
    val result = authRepository.signOut()

    // Assert
    Assert.assertTrue("Sign out should be successful", result.isSuccess)

    currentUser = authRepository.currentUser.first()
    Assert.assertNull("currentUser state flow should be null after sign out", currentUser)
  }
}
