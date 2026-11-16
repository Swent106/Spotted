// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.notification

import com.android.spotted.model.alert.Alert

/** Shows the user system notifications, so ViewModels can ask for one without a `Context`. */
interface Notifier {

  /** Tells the owner that [notification]'s pet was seen; tapping it opens "My alert". */
  fun notifySighting(notification: SightingNotification)

  /** Tells the user that [alert]'s pet was lost [distanceKm] from their home; tapping opens it. */
  fun notifyNearbyAlert(alert: Alert, distanceKm: Double)
}
