// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.fakes

import com.android.spotted.model.thanks.AlreadyThankedException
import com.android.spotted.model.thanks.Thanks
import com.android.spotted.model.thanks.ThanksRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [ThanksRepository] for ViewModel tests.
 * - Like the real one, a second thanks for the same alert and helper fails with
 *   [AlreadyThankedException], and thanking yourself fails with [IllegalArgumentException].
 * - [failure]: set it to make every call fail with it.
 * - The fake does not check the alert or the sighting (the Firestore rules do).
 */
class FakeThanksRepository : ThanksRepository {
  private val thanksById = MutableStateFlow<Map<String, Thanks>>(emptyMap())

  var failure: Throwable? = null

  override suspend fun thank(thanks: Thanks): Result<Unit> {
    failure?.let {
      return Result.failure(it)
    }
    if (thanks.helperId == thanks.ownerId) {
      return Result.failure(IllegalArgumentException("A user can't thank themselves"))
    }
    if (thanks.id in thanksById.value) {
      return Result.failure(AlreadyThankedException(thanks.alertId, thanks.helperId))
    }
    thanksById.update { it + (thanks.id to thanks) }
    return Result.success(Unit)
  }

  override suspend fun getThanksCount(helperId: String): Result<Int> {
    failure?.let {
      return Result.failure(it)
    }
    return Result.success(thanksById.value.values.count { it.helperId == helperId })
  }

  override fun observeThanksReceived(helperId: String): Flow<Result<List<Thanks>>> {
    failure?.let {
      return flowOf(Result.failure(it))
    }
    return thanksById.map { all ->
      Result.success(
          all.values
              .filter { it.helperId == helperId }
              .sortedWith(compareByDescending<Thanks> { it.thankedAtMillis }.thenBy { it.id })
              .take(ThanksRepository.MAX_THANKS_RECEIVED)
      )
    }
  }
}
