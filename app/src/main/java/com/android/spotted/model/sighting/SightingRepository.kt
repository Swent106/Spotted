package com.android.spotted.model.sighting

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
}
