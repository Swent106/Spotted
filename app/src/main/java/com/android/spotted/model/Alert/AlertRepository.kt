// Portions of this code were generated with the help of Copilot.
package com.android.spotted.model.alert

import com.android.spotted.model.Location

interface AlertRepository {
  fun getNewId(): String

  suspend fun publishAlert(alert: Alert): Result<Unit>

  /** Returns the alert with [id], or null when no such alert exists. */
  suspend fun getAlertById(id: String): Result<Alert?>

  /**
   * Returns open alerts within the inclusive [radiusKm] of [center], filtered by exact distance and
   * ordered newest first. Alerts with the same [Alert.lostAtMillis] are ordered by ascending
   * [Alert.id].
   */
  suspend fun getOpenAlertsNear(
      center: Location,
      radiusKm: Double,
  ): Result<List<Alert>>

  /** Returns the open alerts published by [ownerId], wherever they are, newest first. */
  suspend fun getOpenAlertsByOwner(ownerId: String): Result<List<Alert>>

  /** Deletes every alert published by [ownerId], e.g. when they delete their account. */
  suspend fun deleteAlertsByOwner(ownerId: String): Result<Unit>
}
