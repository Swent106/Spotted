// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.notification

import kotlinx.coroutines.flow.Flow

/** Gives an owner the sightings waiting for their review, across all their open alerts. */
interface NotificationRepository {

  /**
   * Live list of the pending sightings on the open alerts of [ownerId], newest first (ties broken
   * by ascending sighting id). Emits again when a sighting is reported or reviewed. A failure is
   * emitted as [Result.failure]; the flow never throws.
   */
  fun observePendingSightings(ownerId: String): Flow<Result<List<SightingNotification>>>
}
