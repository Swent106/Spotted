package com.android.spotted.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAuthRepository : AuthRepository {

  private val _currentUser = MutableStateFlow<User?>(null)
  override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

  /** Set this to true in your tests to simulate an authentication failure. */
  var shouldSimulateFailure = false

  override suspend fun signInWithGoogle(user: User): Result<User> {
    return if (shouldSimulateFailure) {
      Result.failure(Exception("Simulated authentication failure in FakeAuthRepository"))
    } else {
      _currentUser.value = user
      Result.success(user)
    }
  }

  override suspend fun signOut(): Result<Unit> {
    // Clear the state flow to simulate logout
    _currentUser.value = null
    return Result.success(Unit)
  }
}
