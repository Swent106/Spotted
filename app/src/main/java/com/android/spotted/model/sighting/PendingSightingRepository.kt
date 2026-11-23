// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.sighting

import kotlinx.coroutines.flow.Flow

/**
 * A sighting kept on the phone until it can be sent.
 *
 * @property sighting The sighting as it will be sent. Its id comes from
 *   [SightingRepository.getNewId] when it is queued, so sending it twice writes the same document,
 *   and its [Sighting.seenAtMillis] is when the pet was seen, not when it is sent.
 * @property photoUri Local URI of the copy of its photo kept on the phone, if it has one.
 */
data class PendingSighting(
    val sighting: Sighting,
    val photoUri: String?,
)

/**
 * Sightings that could not be sent yet, kept on the phone so they survive the app being closed.
 *
 * Contract shared by every implementation (on-device and fakes):
 * - errors are returned as [Result.failure], never thrown to the caller;
 * - implementations are main-safe (blocking I/O runs off the main thread);
 * - sightings are kept in the order they were added; the sender sends them in that order once the
 *   network is back, and removes each one with [delete].
 */
interface PendingSightingRepository {

  /**
   * Queues [sighting], with the photo at [photoUri] if there is one. The photo is copied onto the
   * phone, so it can still be sent after the app is closed. Adding the same id again replaces it
   * and keeps its place in the queue.
   *
   * Once it is queued, the sighting is sent automatically when the network is back: the caller does
   * not need to do anything else.
   */
  suspend fun add(sighting: Sighting, photoUri: String? = null): Result<Unit>

  /**
   * Returns the queued sightings, oldest first. An empty list, not a failure, when there are none.
   */
  suspend fun list(): Result<List<PendingSighting>>

  /** Removes the sighting [sightingId] and its photo copy. Removing a missing id succeeds. */
  suspend fun delete(sightingId: String): Result<Unit>

  /**
   * Emits the queued sightings right away, oldest first, then again every time the queue changes,
   * even when its size stays the same (a sighting replaced, or one sent and another added). Emits a
   * [Result.failure] when the queue can't be read.
   */
  fun observeQueue(): Flow<Result<List<PendingSighting>>>
}
