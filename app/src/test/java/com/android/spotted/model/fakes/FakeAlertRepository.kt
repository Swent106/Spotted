package com.android.spotted.model.fakes

import com.android.spotted.model.Location
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertRepository
import com.android.spotted.model.alert.AlertStatus
import com.android.spotted.model.distanceKm

class FakeAlertRepository(private val now: () -> Long = { 0L }) : AlertRepository {
  private val alerts = linkedMapOf<String, Alert>()
  private var nextId = 0

  var failure: Throwable? = null

  val publishedAlerts: List<Alert>
    get() = alerts.values.toList()

  override fun getNewId(): String = "alert-${++nextId}"

  override suspend fun publishAlert(alert: Alert): Result<Unit> {
    failure?.let {
      return Result.failure(it)
    }
    alerts[alert.id] = alert
    return Result.success(Unit)
  }

  override suspend fun getAlertById(id: String): Result<Alert?> {
    failure?.let {
      return Result.failure(it)
    }
    return Result.success(alerts[id])
  }

  override suspend fun getMyAlerts(ownerId: String): Result<List<Alert>> {
    failure?.let {
      return Result.failure(it)
    }
    return Result.success(
        alerts.values
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
        alerts[alertId]
            ?: return Result.failure(NoSuchElementException("No alert with id $alertId"))
    // Closing is one-way: an already closed alert keeps its original closing time
    if (alert.status == AlertStatus.CLOSED) return Result.success(Unit)
    alerts[alertId] = alert.copy(status = AlertStatus.CLOSED, closedAtMillis = now())
    return Result.success(Unit)
  }

  override suspend fun getOpenAlertsByOwner(ownerId: String): Result<List<Alert>> {
    failure?.let {
      return Result.failure(it)
    }
    return Result.success(
        alerts.values
            .filter { it.ownerId == ownerId && it.status == AlertStatus.OPEN }
            .sortedWith(compareByDescending<Alert> { it.lostAtMillis }.thenBy { it.id })
    )
  }

  override suspend fun deleteAlertsByOwner(ownerId: String): Result<Unit> {
    failure?.let {
      return Result.failure(it)
    }
    alerts.values.removeAll { it.ownerId == ownerId }
    return Result.success(Unit)
  }

  override suspend fun getOpenAlertsNear(
      center: Location,
      radiusKm: Double,
  ): Result<List<Alert>> {
    failure?.let {
      return Result.failure(it)
    }
    val nearbyAlerts =
        alerts.values
            .asSequence()
            .filter { it.status == AlertStatus.OPEN }
            .filter { center.distanceKm(it.lastKnownLocation) <= radiusKm }
            .sortedWith(compareByDescending<Alert> { it.lostAtMillis }.thenBy { it.id })
            .toList()
    return Result.success(nearbyAlerts)
  }
}
