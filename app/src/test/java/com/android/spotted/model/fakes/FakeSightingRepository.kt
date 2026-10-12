package com.android.spotted.model.fakes

import com.android.spotted.model.sighting.Sighting
import com.android.spotted.model.sighting.SightingRepository

/**
 * In-memory [SightingRepository] for ViewModel tests.
 * - Ids are deterministic: "sighting-1", "sighting-2", …
 * - [reportFailure] / [queryFailure]: set one to make the matching calls fail with it.
 * - [reportedSightings]: everything reported, in order, so tests can check what was saved.
 */
class FakeSightingRepository : SightingRepository {
  private val sightings = linkedMapOf<String, Sighting>()
  private var nextId = 0

  var reportFailure: Throwable? = null
  var queryFailure: Throwable? = null

  val reportedSightings: List<Sighting>
    get() = sightings.values.toList()

  override fun getNewId(): String = "sighting-${++nextId}"

  override suspend fun reportSighting(sighting: Sighting): Result<Unit> {
    reportFailure?.let {
      return Result.failure(it)
    }
    sightings[sighting.id] = sighting
    return Result.success(Unit)
  }

  override suspend fun getSightingsForAlert(alertId: String): Result<List<Sighting>> {
    queryFailure?.let {
      return Result.failure(it)
    }
    return Result.success(
        sightings.values
            .filter { it.alertId == alertId }
            .sortedWith(compareByDescending<Sighting> { it.seenAtMillis }.thenBy { it.id }))
  }
}
