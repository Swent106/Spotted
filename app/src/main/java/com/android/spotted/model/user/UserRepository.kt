package com.android.spotted.model.user

interface UserRepository {
  suspend fun getUser(uid: String): Result<UserProfile>

  suspend fun saveUser(user: UserProfile): Result<Unit>

  /** Deletes the profile of user [uid]. Deleting a profile that doesn't exist succeeds. */
  suspend fun deleteUser(uid: String): Result<Unit>
}
