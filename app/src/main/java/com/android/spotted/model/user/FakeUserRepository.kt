package com.android.spotted.model.user

class FakeUserRepository : UserRepository {
  private val users = linkedMapOf<String, UserProfile>()

  var failure: Throwable? = null

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
    failure?.let {
      return Result.failure(it)
    }
    users[user.uid] = user
    return Result.success(Unit)
  }
}
