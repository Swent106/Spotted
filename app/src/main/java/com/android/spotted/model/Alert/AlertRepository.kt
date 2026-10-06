package com.android.spotted.model.alert

import com.android.spotted.model.Location

interface AlertRepository {
  fun getNewId(): String

  suspend fun publishAlert(alert: Alert): Result<Unit>

  /**
   * Returns open alerts within the inclusive [radiusKm] of [center], filtered by exact distance and
   * ordered newest first. Alerts with the same [Alert.lostAtMillis] are ordered by ascending
   * [Alert.id].
   */
  suspend fun getOpenAlertsNear(
      center: Location,
      radiusKm: Double,
  ): Result<List<Alert>>
}
