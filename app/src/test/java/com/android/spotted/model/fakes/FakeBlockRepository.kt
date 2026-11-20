// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.fakes

import com.android.spotted.model.moderation.BlockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [BlockRepository] for ViewModel tests.
 * - Each user has their own list, and [observeBlocked] is live: blocking or unblocking emits again.
 * - [failure]: set it to make every call fail with it.
 * - Like the real one, blocking yourself fails, and blocking twice or unblocking someone not
 *   blocked succeeds.
 */
class FakeBlockRepository : BlockRepository {
  private val blocked = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

  var failure: Throwable? = null

  /** The block list of [userId], as stored. */
  fun blockedBy(userId: String): Set<String> = blocked.value[userId].orEmpty()

  override fun observeBlocked(userId: String): Flow<Result<Set<String>>> {
    failure?.let {
      return flowOf(Result.failure(it))
    }
    return blocked.map { Result.success(it[userId].orEmpty()) }.distinctUntilChanged()
  }

  override suspend fun block(userId: String, blockedUserId: String): Result<Unit> {
    failure?.let {
      return Result.failure(it)
    }
    if (userId == blockedUserId) {
      return Result.failure(IllegalArgumentException("A user can't block themselves"))
    }
    blocked.update { it + (userId to (it[userId].orEmpty() + blockedUserId)) }
    return Result.success(Unit)
  }

  override suspend fun unblock(userId: String, blockedUserId: String): Result<Unit> {
    failure?.let {
      return Result.failure(it)
    }
    blocked.update { it + (userId to (it[userId].orEmpty() - blockedUserId)) }
    return Result.success(Unit)
  }
}
