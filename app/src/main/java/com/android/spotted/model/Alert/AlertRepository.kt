// Portions of this code were generated with the help of Copilot.
// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.alert

import com.android.spotted.model.Location
import kotlinx.coroutines.flow.Flow

interface AlertRepository {
  fun getNewId(): String

  /**
   * Publishes [alert]. Fails with [AlertAlreadyOpenException] if its pet already has an open alert,
   * also when two publishes for the same pet race (from two taps or two devices).
   */
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
   * Moves the last seen place of the open alert [alertId] to [location], seen at [seenAtMillis],
   * when that is newer than its current [Alert.lastSeenAt]. Returns true if the alert moved, false
   * if it was left as it is (older or same time, or alert closed). Fails with
   * [NoSuchElementException] if it doesn't exist.
   */
  suspend fun updateLastSeen(
      alertId: String,
      location: Location,
      seenAtMillis: Long,
  ): Result<Boolean>

  /**
   * Returns open alerts within the inclusive [radiusKm] of [center], filtered by exact distance and
   * ordered newest first. Alerts with the same [Alert.lostAtMillis] are ordered by ascending
   * [Alert.id].
   */
  suspend fun getOpenAlertsNear(
      center: Location,
      radiusKm: Double,
  ): Result<List<Alert>>

  /**
   * Emits the open alerts near [center] right away, then again after every change. Same filtering
   * and order as [getOpenAlertsNear]. Only lists confirmed by the server are emitted, never ones
   * from the local cache. An error is emitted as a failure and ends the flow.
   */
  fun observeOpenAlertsNear(center: Location, radiusKm: Double): Flow<Result<List<Alert>>>

  /** Returns up to [limit] open alerts near [center] from the local cache, newest first. */
  suspend fun getCachedOpenAlertsNear(
      center: Location,
      radiusKm: Double,
      limit: Int = 5,
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
