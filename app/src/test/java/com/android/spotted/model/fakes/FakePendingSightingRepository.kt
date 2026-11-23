// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.fakes

import com.android.spotted.model.sighting.PendingSighting
import com.android.spotted.model.sighting.PendingSightingRepository
import com.android.spotted.model.sighting.Sighting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [PendingSightingRepository] for ViewModel and sender tests.
 * - The photo is not copied: [PendingSighting.photoUri] is the URI given to [add].
 * - [addFailure] / [listFailure] / [deleteFailure] / [queueFailure]: set one to make the matching
 *   calls fail with it ([queueFailure]: [observeQueue] emits it).
 * - [pending]: what is queued, oldest first, so tests can check it.
 * - [addCount]: how many times [add] succeeded, i.e. how many times a send was asked for.
 */
class FakePendingSightingRepository(initial: List<PendingSighting> = emptyList()) :
    PendingSightingRepository {
  private val queue = MutableStateFlow(initial)

  var addFailure: Throwable? = null
  var listFailure: Throwable? = null
  var deleteFailure: Throwable? = null
  var queueFailure: Throwable? = null

  var addCount = 0
    private set

  val pending: List<PendingSighting>
    get() = queue.value

  override suspend fun add(sighting: Sighting, photoUri: String?): Result<Unit> {
    addFailure?.let {
      return Result.failure(it)
    }
    val entry = PendingSighting(sighting, photoUri)
    queue.update { current ->
      if (current.any { it.sighting.id == sighting.id }) {
        current.map { if (it.sighting.id == sighting.id) entry else it }
      } else {
        current + entry
      }
    }
    addCount++
    return Result.success(Unit)
  }

  override suspend fun list(): Result<List<PendingSighting>> {
    listFailure?.let {
      return Result.failure(it)
    }
    return Result.success(queue.value)
  }

  override suspend fun delete(sightingId: String): Result<Unit> {
    deleteFailure?.let {
      return Result.failure(it)
    }
    queue.update { current -> current.filterNot { it.sighting.id == sightingId } }
    return Result.success(Unit)
  }

  override fun observeQueue(): Flow<Result<List<PendingSighting>>> = queue.map { current ->
    queueFailure?.let { Result.failure(it) } ?: Result.success(current)
  }
}
