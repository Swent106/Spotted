// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.fakes

import com.android.spotted.model.alert.Alert
import com.android.spotted.model.notification.Notifier
import com.android.spotted.model.notification.SightingNotification

/** [Notifier] that records what would have been posted, in [sightings] and [nearbyAlerts]. */
class FakeNotifier : Notifier {
  val sightings = mutableListOf<SightingNotification>()
  val nearbyAlerts = mutableListOf<Pair<Alert, Double>>()

  override fun notifySighting(notification: SightingNotification) {
    sightings += notification
  }

  override fun notifyNearbyAlert(alert: Alert, distanceKm: Double) {
    nearbyAlerts += alert to distanceKm
  }
}
