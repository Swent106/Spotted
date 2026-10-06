package com.android.spotted.model.Alert

import com.android.spotted.model.Location

interface AlertRepository {
  fun getNewId(): String

  suspend fun publishAlert(alert: Alert): Result<Unit>

  /**
   * Returns open alerts within [radiusKm] of [center], filtered by exact distance and ordered
   * newest first.
   */
  suspend fun getOpenAlertsNear(
      center: Location,
      radiusKm: Double,
  ): Result<List<Alert>>
}
