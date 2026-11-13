// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.notification

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.android.spotted.MainActivity
import com.android.spotted.R
import com.android.spotted.Route
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.formatKm
import com.android.spotted.model.notification.Notifier
import com.android.spotted.model.notification.SightingNotification

/**
 * [Notifier] that posts Android notifications. Creating it registers the notification channels,
 * which is safe to repeat. If the user refused notifications, nothing is posted.
 */
class SystemNotifier(private val context: Context) : Notifier {

  private val manager = NotificationManagerCompat.from(context)

  init {
    manager.createNotificationChannelsCompat(
        listOf(
            channel(CHANNEL_SIGHTINGS, R.string.notification_channel_sightings),
            channel(CHANNEL_NEARBY_ALERTS, R.string.notification_channel_nearby_alerts),
        )
    )
  }

  override fun notifySighting(notification: SightingNotification) {
    post(
        channelId = CHANNEL_SIGHTINGS,
        id = SIGHTING_NOTIFICATION_ID,
        tag = notification.sightingId,
        title = context.getString(R.string.notification_sighting_title, notification.petName),
        text = context.getString(R.string.notification_sighting_text),
        route = Route.myAlert(notification.alertId),
    )
  }

  override fun notifyNearbyAlert(alert: Alert, distanceKm: Double) {
    post(
        channelId = CHANNEL_NEARBY_ALERTS,
        id = NEARBY_ALERT_NOTIFICATION_ID,
        tag = alert.id,
        title =
            context.getString(
                R.string.notification_nearby_alert_title,
                alert.petName,
                formatKm(distanceKm),
            ),
        text = context.getString(R.string.notification_nearby_alert_text),
        route = Route.alertDetail(alert.id),
    )
  }

  private fun channel(id: String, nameRes: Int) =
      NotificationChannelCompat.Builder(id, NotificationManagerCompat.IMPORTANCE_DEFAULT)
          .setName(context.getString(nameRes))
          .build()

  /** Posts one notification; [tag] and [id] identify it, and tapping it opens [route]. */
  // areNotificationsEnabled covers POST_NOTIFICATIONS, but lint only recognises checkSelfPermission
  @SuppressLint("MissingPermission")
  private fun post(
      channelId: String,
      id: Int,
      tag: String,
      title: String,
      text: String,
      route: String,
  ) {
    if (!manager.areNotificationsEnabled()) return
    val notification =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent(route))
            .setAutoCancel(true)
            .build()
    manager.notify(tag, id, notification)
  }

  private fun openIntent(route: String): PendingIntent {
    val intent =
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_ROUTE, route)
            // Extras don't make PendingIntents distinct: without this, every tap opens one route
            .setData(Uri.parse("$URI_PREFIX${Uri.encode(route)}"))
    return PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
  }

  companion object {
    const val CHANNEL_SIGHTINGS = "sightings"
    const val CHANNEL_NEARBY_ALERTS = "nearby_alerts"
    const val SIGHTING_NOTIFICATION_ID = 1
    const val NEARBY_ALERT_NOTIFICATION_ID = 2

    /** Intent extra holding the [Route] a tapped notification opens. */
    const val EXTRA_ROUTE = "com.android.spotted.NOTIFICATION_ROUTE"

    private const val URI_PREFIX = "spotted://notification/"
  }
}
