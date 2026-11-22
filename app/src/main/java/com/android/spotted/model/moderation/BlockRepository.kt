// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.moderation

import kotlinx.coroutines.flow.Flow

/**
 * A user's block list: the users whose alerts and sightings they no longer want to see. It is
 * private to its owner; the blocked users are not told.
 */
interface BlockRepository {

  /**
   * The uids [userId] blocked, then again after every change. A failure is emitted as
   * [Result.failure]; the flow never throws.
   */
  fun observeBlocked(userId: String): Flow<Result<Set<String>>>

  /**
   * Adds [blockedUserId] to the block list of [userId]. Blocking someone already blocked succeeds;
   * blocking yourself fails with [IllegalArgumentException].
   */
  suspend fun block(userId: String, blockedUserId: String): Result<Unit>

  /** Removes [blockedUserId] from the block list of [userId]. A user not blocked succeeds. */
  suspend fun unblock(userId: String, blockedUserId: String): Result<Unit>
}
