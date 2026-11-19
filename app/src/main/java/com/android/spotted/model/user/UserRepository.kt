package com.android.spotted.model.user

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface UserRepository {
  suspend fun getUser(uid: String): Result<UserProfile>

  /**
   * The profile of user [uid], then again after every change, like [getUser] each time. By default
   * it is read once: repositories that can follow changes override it.
   */
  fun observeUser(uid: String): Flow<Result<UserProfile>> = flow { emit(getUser(uid)) }

  suspend fun saveUser(user: UserProfile): Result<Unit>

  /** Deletes the profile of user [uid]. Deleting a profile that doesn't exist succeeds. */
  suspend fun deleteUser(uid: String): Result<Unit>
}
