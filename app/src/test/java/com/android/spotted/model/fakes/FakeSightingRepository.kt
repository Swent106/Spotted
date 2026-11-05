// Portions of this code were generated with the help of Claude (Anthropic).
// Portions of this code were generated with the help of Copilot.
package com.android.spotted.model.fakes

import com.android.spotted.model.sighting.Sighting
import com.android.spotted.model.sighting.SightingRepository
import com.android.spotted.model.sighting.SightingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update

/**
 * In-memory [SightingRepository] for ViewModel tests.
 * - Ids are deterministic: "sighting-1", "sighting-2", …
 * - [reportFailure] / [queryFailure] / [observeFailure] / [statusFailure]: set one to make the
 *   matching calls fail with it.
 * - [reportedSightings]: everything reported, in order, so tests can check what was saved.
 * - [observeSightingsForAlert] is live: reporting a sighting or changing a status emits again.
 * - Like Firestore, reporting an existing id fails once it was reviewed or moved to another alert.
 * - The fake does not know who is signed in, so it does not check that the caller owns the alert
 *   (the Firestore rules do).
 */
class FakeSightingRepository : SightingRepository {
  private val sightings = MutableStateFlow<Map<String, Sighting>>(emptyMap())
  private val observationEvents = MutableSharedFlow<Result<List<Sighting>>>()
  private var nextId = 0

  var reportFailure: Throwable? = null
  var queryFailure: Throwable? = null
  var observeFailure: Throwable? = null
  var statusFailure: Throwable? = null

  suspend fun emitObservationFailure(error: Throwable) {
    observationEvents.emit(Result.failure(error))
  }

  suspend fun emitObservationSuccess(sightings: List<Sighting>) {
    observationEvents.emit(Result.success(sightings))
  }

  val reportedSightings: List<Sighting>
    get() = sightings.value.values.toList()

  override fun getNewId(): String = "sighting-${++nextId}"

  override suspend fun reportSighting(sighting: Sighting): Result<Unit> {
    reportFailure?.let {
      return Result.failure(it)
    }
    // Same checks as the Firestore rules on a sighting that already exists
    val saved = sightings.value[sighting.id]
    if (
        saved != null &&
            (saved.status != SightingStatus.PENDING ||
                (saved.alertId != null && saved.alertId != sighting.alertId))
    ) {
      return Result.failure(IllegalStateException("Sighting ${sighting.id} cannot be changed"))
    }
    sightings.update { it + (sighting.id to sighting) }
    return Result.success(Unit)
  }

  override suspend fun getSightingsForAlert(alertId: String): Result<List<Sighting>> {
    queryFailure?.let {
      return Result.failure(it)
    }
    return Result.success(sightingsOf(sightings.value, alertId))
  }

  override fun observeSightingsForAlert(alertId: String): Flow<Result<List<Sighting>>> {
    observeFailure?.let {
      return flowOf(Result.failure(it))
    }
    val liveSightings =
        sightings.map { sightingsOf(it, alertId) }.distinctUntilChanged().map { Result.success(it) }
    return merge(liveSightings, observationEvents)
  }

  override suspend fun setSightingStatus(sightingId: String, status: SightingStatus): Result<Unit> {
    statusFailure?.let {
      return Result.failure(it)
    }
    val sighting =
        sightings.value[sightingId]
            ?: return Result.failure(NoSuchElementException("No sighting $sightingId"))
    if (sighting.alertId == null) {
      return Result.failure(IllegalStateException("Sighting $sightingId has no alert"))
    }
    sightings.update { it + (sightingId to sighting.copy(status = status)) }
    return Result.success(Unit)
  }

  private fun sightingsOf(all: Map<String, Sighting>, alertId: String): List<Sighting> =
      all.values
          .filter { it.alertId == alertId }
          .sortedWith(compareByDescending<Sighting> { it.seenAtMillis }.thenBy { it.id })
}
