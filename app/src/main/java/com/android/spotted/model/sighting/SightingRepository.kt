// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.sighting

import kotlinx.coroutines.flow.Flow

/**
 * Stores and reads pet sightings.
 *
 * Contract shared by every implementation (Firestore and fakes):
 * - errors are returned as [Result.failure], never thrown to the caller;
 * - implementations are main-safe (blocking I/O runs off the main thread).
 */
interface SightingRepository {

  /** Returns a new unique id to use for a [Sighting] before reporting it. */
  fun getNewId(): String

  /** Saves [sighting]. Reporting the same id again replaces the previous sighting. */
  suspend fun reportSighting(sighting: Sighting): Result<Unit>

  /**
   * Returns the sightings linked to the alert [alertId], most recently seen first (ties broken by
   * id). Returns an empty list, not a failure, when there are none.
   */
  suspend fun getSightingsForAlert(alertId: String): Result<List<Sighting>>

  /**
   * Live version of [getSightingsForAlert]: emits the current sightings of the alert [alertId]
   * right away, then again every time one is added, changed or removed. Same order, and an empty
   * list when there are none.
   *
   * An error is emitted as a [Result.failure] and ends the flow. The underlying listener is removed
   * when the collector is cancelled.
   */
  fun observeSightingsForAlert(alertId: String): Flow<Result<List<Sighting>>>

  /**
   * Sets the [status] of the sighting [sightingId], without changing anything else.
   *
   * Only the owner of the alert the sighting is linked to is allowed to do it. Fails if the
   * sighting does not exist, is not linked to an alert, or the user is not allowed.
   */
  suspend fun setSightingStatus(sightingId: String, status: SightingStatus): Result<Unit>
}
