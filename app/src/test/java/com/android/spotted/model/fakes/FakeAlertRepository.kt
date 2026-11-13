// Portions of this code were generated with the help of Copilot.
// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.fakes

import com.android.spotted.model.Location
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertAlreadyOpenException
import com.android.spotted.model.alert.AlertRepository
import com.android.spotted.model.alert.AlertStatus
import com.android.spotted.model.distanceKm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [AlertRepository] for tests. [observeOpenAlertsNear] is live: publishing, closing or
 * deleting an alert emits again.
 */
class FakeAlertRepository(private val now: () -> Long = { 0L }) : AlertRepository {
  private val alerts = MutableStateFlow<Map<String, Alert>>(emptyMap())
  private var nextId = 0

  var failure: Throwable? = null
  var cachedAlerts: List<Alert> = emptyList()
  var cachedAlertsFailure: Throwable? = null

  val publishedAlerts: List<Alert>
    get() = alerts.value.values.toList()

  override fun getNewId(): String = "alert-${++nextId}"

  override suspend fun publishAlert(alert: Alert): Result<Unit> {
    failure?.let {
      return Result.failure(it)
    }
    // Like the real repository: a pet has at most one open alert
    val open =
        alerts.value.values.firstOrNull {
          it.petId == alert.petId && it.id != alert.id && it.status == AlertStatus.OPEN
        }
    if (open != null) return Result.failure(AlertAlreadyOpenException(open.id))
    alerts.update { it + (alert.id to alert) }
    return Result.success(Unit)
  }

  override suspend fun getAlertById(id: String): Result<Alert?> {
    failure?.let {
      return Result.failure(it)
    }
    return Result.success(alerts.value[id])
  }

  override suspend fun getMyAlerts(ownerId: String): Result<List<Alert>> {
    failure?.let {
      return Result.failure(it)
    }
    return Result.success(
        alerts.value.values
            .filter { it.ownerId == ownerId }
            .sortedWith(compareByDescending<Alert> { it.lostAtMillis }.thenBy { it.id })
            .take(AlertRepository.MY_ALERTS_LIMIT)
    )
  }

  override suspend fun closeAlert(alertId: String): Result<Unit> {
    failure?.let {
      return Result.failure(it)
    }
    val alert =
        alerts.value[alertId]
            ?: return Result.failure(NoSuchElementException("No alert with id $alertId"))
    // Closing is one-way: an already closed alert keeps its original closing time
    if (alert.status == AlertStatus.CLOSED) return Result.success(Unit)
    val closed = alert.copy(status = AlertStatus.CLOSED, closedAtMillis = now())
    alerts.update { it + (alertId to closed) }
    return Result.success(Unit)
  }

  override suspend fun updateLastSeen(
      alertId: String,
      location: Location,
      seenAtMillis: Long,
  ): Result<Boolean> {
    failure?.let {
      return Result.failure(it)
    }
    val alert =
        alerts.value[alertId]
            ?: return Result.failure(NoSuchElementException("No alert with id $alertId"))
    if (alert.status == AlertStatus.CLOSED || seenAtMillis <= alert.lastSeenAt) {
      return Result.success(false)
    }
    val moved = alert.copy(lastKnownLocation = location, lastSeenAtMillis = seenAtMillis)
    alerts.update { it + (alertId to moved) }
    return Result.success(true)
  }

  override suspend fun getOpenAlertsByOwner(ownerId: String): Result<List<Alert>> {
    failure?.let {
      return Result.failure(it)
    }
    return Result.success(
        alerts.value.values
            .filter { it.ownerId == ownerId && it.status == AlertStatus.OPEN }
            .sortedWith(compareByDescending<Alert> { it.lostAtMillis }.thenBy { it.id })
    )
  }

  override suspend fun deleteAlertsByOwner(ownerId: String): Result<Unit> {
    failure?.let {
      return Result.failure(it)
    }
    alerts.update { current -> current.filterValues { it.ownerId != ownerId } }
    return Result.success(Unit)
  }

  override suspend fun getOpenAlertsNear(
      center: Location,
      radiusKm: Double,
  ): Result<List<Alert>> {
    failure?.let {
      return Result.failure(it)
    }
    return Result.success(openAlertsNear(alerts.value.values, center, radiusKm))
  }

  override fun observeOpenAlertsNear(
      center: Location,
      radiusKm: Double,
  ): Flow<Result<List<Alert>>> {
    failure?.let {
      return flowOf(Result.failure(it))
    }
    return alerts
        .map { openAlertsNear(it.values, center, radiusKm) }
        .distinctUntilChanged()
        .map { Result.success(it) }
  }

  override suspend fun getCachedOpenAlertsNear(
      center: Location,
      radiusKm: Double,
      limit: Int,
  ): Result<List<Alert>> {
    cachedAlertsFailure?.let {
      return Result.failure(it)
    }
    if (limit <= 0) return Result.failure(IllegalArgumentException("limit must be positive"))
    return Result.success(openAlertsNear(cachedAlerts, center, radiusKm).take(limit))
  }

  /** The open alerts within the inclusive [radiusKm] of [center], newest first, ties by id. */
  private fun openAlertsNear(
      alerts: Collection<Alert>,
      center: Location,
      radiusKm: Double,
  ): List<Alert> =
      alerts
          .filter { it.status == AlertStatus.OPEN }
          .filter { center.distanceKm(it.lastKnownLocation) <= radiusKm }
          .sortedWith(compareByDescending<Alert> { it.lostAtMillis }.thenBy { it.id })
}
