// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.utils

import com.android.spotted.model.Location
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertRepository
import kotlinx.coroutines.flow.flowOf

/**
 * [AlertRepository] for UI tests that only need some open alerts: every query returns [openAlerts]
 * and every write succeeds without changing anything.
 */
class FakeOpenAlertsRepository(private val openAlerts: List<Alert> = emptyList()) :
    AlertRepository {
  override fun getNewId() = "alert-new"

  override suspend fun publishAlert(alert: Alert) = Result.success(Unit)

  override suspend fun getAlertById(id: String) =
      Result.success(openAlerts.firstOrNull { it.id == id })

  override suspend fun getMyAlerts(ownerId: String) = Result.success(openAlerts)

  override suspend fun closeAlert(alertId: String) = Result.success(Unit)

  override suspend fun getOpenAlertsNear(center: Location, radiusKm: Double) =
      Result.success(openAlerts)

  override fun observeOpenAlertsNear(center: Location, radiusKm: Double) =
      flowOf(Result.success(openAlerts))

  override suspend fun getCachedOpenAlertsNear(center: Location, radiusKm: Double, limit: Int) =
      Result.success(openAlerts)

  override suspend fun getOpenAlertsByOwner(ownerId: String) = Result.success(openAlerts)

  override suspend fun deleteAlertsByOwner(ownerId: String) = Result.success(Unit)
}
