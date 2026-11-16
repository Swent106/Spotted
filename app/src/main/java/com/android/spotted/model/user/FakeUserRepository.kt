// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.user

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [UserRepository]. [observeUser] is live: saving or deleting a profile emits again. */
class FakeUserRepository : UserRepository {
  private val users = MutableStateFlow<Map<String, UserProfile>>(emptyMap())

  var failure: Throwable? = null
  var saveCalls: Int = 0
    private set

  override suspend fun getUser(uid: String): Result<UserProfile> = find(uid, users.value[uid])

  override fun observeUser(uid: String): Flow<Result<UserProfile>> =
      users.map { it[uid] }.distinctUntilChanged().map { find(uid, it) }

  override suspend fun saveUser(user: UserProfile): Result<Unit> {
    saveCalls += 1
    failure?.let {
      return Result.failure(it)
    }
    users.update { it + (user.uid to user) }
    return Result.success(Unit)
  }

  override suspend fun deleteUser(uid: String): Result<Unit> {
    failure?.let {
      return Result.failure(it)
    }
    users.update { it - uid }
    return Result.success(Unit)
  }

  private fun find(uid: String, user: UserProfile?): Result<UserProfile> {
    failure?.let {
      return Result.failure(it)
    }
    return if (user != null) {
      Result.success(user)
    } else {
      Result.failure(NoSuchElementException("User with uid $uid was not found"))
    }
  }
}
