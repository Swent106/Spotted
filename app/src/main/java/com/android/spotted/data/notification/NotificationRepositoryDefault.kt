// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.notification

import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertRepository
import com.android.spotted.model.notification.NotificationRepository
import com.android.spotted.model.notification.SightingNotification
import com.android.spotted.model.sighting.Sighting
import com.android.spotted.model.sighting.SightingRepository
import com.android.spotted.model.sighting.SightingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * [NotificationRepository] built on the alert and sighting repositories, so it needs no database
 * code of its own: it loads the owner's open alerts once, then watches the sightings of each one.
 *
 * An alert opened after the flow started is only picked up when it is collected again (e.g. the
 * screen is reopened), since [AlertRepository.getOpenAlertsByOwner] is a one-shot query.
 */
class NotificationRepositoryDefault(
    private val alertRepository: AlertRepository,
    private val sightingRepository: SightingRepository,
) : NotificationRepository {

  override fun observePendingSightings(ownerId: String): Flow<Result<List<SightingNotification>>> =
      flow {
        val alerts =
            alertRepository.getOpenAlertsByOwner(ownerId).getOrElse {
              emit(Result.failure(it))
              return@flow
            }
        if (alerts.isEmpty()) {
          emit(Result.success(emptyList()))
          return@flow
        }
        val perAlert = alerts.map { alert -> pendingSightingsOf(alert) }
        emitAll(combine(perAlert) { results -> merge(results.toList()) })
      }

  private fun pendingSightingsOf(alert: Alert): Flow<Result<List<SightingNotification>>> =
      sightingRepository.observeSightingsForAlert(alert.id).map { result ->
        result.map { sightings ->
          sightings.filter { it.status == SightingStatus.PENDING }.map { it.toNotification(alert) }
        }
      }

  /** One list for all alerts, newest first; any failing alert makes the whole result fail. */
  private fun merge(
      results: List<Result<List<SightingNotification>>>
  ): Result<List<SightingNotification>> {
    results
        .firstNotNullOfOrNull { it.exceptionOrNull() }
        ?.let {
          return Result.failure(it)
        }
    return Result.success(
        results
            .flatMap { it.getOrThrow() }
            .sortedWith(
                compareByDescending<SightingNotification> { it.seenAtMillis }
                    .thenBy { it.sightingId }
            )
    )
  }

  private fun Sighting.toNotification(alert: Alert) =
      SightingNotification(
          sightingId = id,
          alertId = alert.id,
          petName = alert.petName,
          sightingPhotoUrl = photoUrl,
          seenAtMillis = seenAtMillis,
      )
}
