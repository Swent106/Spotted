// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.notification

/** Shows the user system notifications, so ViewModels can ask for one without a `Context`. */
interface Notifier {

  /** Tells the owner that [notification]'s pet was seen; tapping it opens "My alert". */
  fun notifySighting(notification: SightingNotification)
}
