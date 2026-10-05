package com.android.spotted.model.auth

import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {

  val currentUser: StateFlow<User?>

  suspend fun signInWithGoogle(idToken: String): Result<User>

  /** Signs out the current user. */
  suspend fun signOut(): Result<Unit>
}
