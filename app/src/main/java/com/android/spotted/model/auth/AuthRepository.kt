package com.android.spotted.model.auth

import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {

  val currentUser: StateFlow<User?>

  suspend fun signInWithGoogle(idToken: String): Result<User>

  /** Signs out the current user. */
  suspend fun signOut(): Result<Unit>

  /**
   * Deletes the signed-in user's account, which also signs them out. Firebase may refuse it if the
   * user signed in too long ago; signing in again then lets it through.
   */
  suspend fun deleteAccount(): Result<Unit>
}
