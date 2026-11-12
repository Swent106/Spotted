// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.fakes

import com.android.spotted.model.notification.Notifier
import com.android.spotted.model.notification.SightingNotification

/** [Notifier] that records what would have been posted, in [sightings]. */
class FakeNotifier : Notifier {
  val sightings = mutableListOf<SightingNotification>()

  override fun notifySighting(notification: SightingNotification) {
    sightings += notification
  }
}
