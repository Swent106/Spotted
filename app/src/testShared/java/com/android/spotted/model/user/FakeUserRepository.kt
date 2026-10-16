// Portions of this code were generated with the help of GitHub Copilot.
package com.android.spotted.model.user

import java.util.concurrent.ConcurrentHashMap

class FakeUserRepository : UserRepository {
  private val users = ConcurrentHashMap<String, UserProfile>()

  var failure: Throwable? = null
  var saveCalls: Int = 0
    private set

  override suspend fun getUser(uid: String): Result<UserProfile> {
    failure?.let {
      return Result.failure(it)
    }
    val user = users[uid]
    return if (user != null) {
      Result.success(user)
    } else {
      Result.failure(NoSuchElementException("User with uid $uid was not found"))
    }
  }

  override suspend fun saveUser(user: UserProfile): Result<Unit> {
    saveCalls += 1
    failure?.let {
      return Result.failure(it)
    }
    users[user.uid] = user
    return Result.success(Unit)
  }
}
