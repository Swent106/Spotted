// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.thanks

import kotlinx.coroutines.flow.Flow

/**
 * Thanks from owners to the helpers of their closed alerts.
 *
 * Who may thank whom (the alert's owner, once it is closed, only a helper who sent a sighting on
 * it, never themselves) is checked by the Firestore rules.
 *
 * Contract shared by every implementation (Firestore and fakes):
 * - errors are returned as [Result.failure], never thrown to the caller;
 * - implementations are main-safe (blocking I/O runs off the main thread).
 */
interface ThanksRepository {

  /**
   * Stores [thanks]. A helper is thanked once per alert: a second thanks for the same alert and
   * helper fails with [AlreadyThankedException] and changes nothing. Thanking yourself fails with
   * [IllegalArgumentException].
   */
  suspend fun thank(thanks: Thanks): Result<Unit>

  /** How many times [helperId] was thanked, over all alerts: 0 when never. */
  suspend fun getThanksCount(helperId: String): Result<Int>

  /**
   * The thanks [helperId] received, newest first (ties broken by ascending [Thanks.id]), then again
   * after every change. At most [MAX_THANKS_RECEIVED]. A failure is emitted as [Result.failure];
   * the flow never throws.
   */
  fun observeThanksReceived(helperId: String): Flow<Result<List<Thanks>>>

  companion object {
    /** Most thanks [observeThanksReceived] lists. */
    const val MAX_THANKS_RECEIVED = 100
  }
}
