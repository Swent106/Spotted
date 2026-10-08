package com.android.spotted.model.user

interface UserRepository {
  suspend fun getUser(uid: String): Result<UserProfile>

  suspend fun saveUser(user: UserProfile): Result<Unit>
}
