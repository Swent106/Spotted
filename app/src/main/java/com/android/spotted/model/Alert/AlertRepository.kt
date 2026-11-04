// Portions of this code were generated with the help of Copilot.
package com.android.spotted.model.alert

import com.android.spotted.model.Location

interface AlertRepository {
  fun getNewId(): String

  suspend fun publishAlert(alert: Alert): Result<Unit>

  /** Returns the alert with [id], or null when no such alert exists. */
  suspend fun getAlertById(id: String): Result<Alert?>

  /**
   * Returns the alerts published by [ownerId], open and closed, newest first (ties broken by
   * ascending [Alert.id]). At most the [MY_ALERTS_LIMIT] newest ones.
   */
  suspend fun getMyAlerts(ownerId: String): Result<List<Alert>>

  /**
   * Closes the alert [alertId] and stamps its closing time. Closing is one-way: an alert already
   * closed keeps its first closing time. Fails with [NoSuchElementException] if it doesn't exist.
   */
  suspend fun closeAlert(alertId: String): Result<Unit>

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

  companion object {
    /** Most alerts [getMyAlerts] returns; also the most the Firestore rules let a query list. */
    const val MY_ALERTS_LIMIT = 100
  }
}
