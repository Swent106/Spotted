// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.notification

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.test.platform.app.InstrumentationRegistry
import com.android.spotted.model.notification.SightingNotification
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** [SystemNotifier] on a real device: Android itself reports the channels and the notification. */
class SystemNotifierInstrumentedTest {

  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context = instrumentation.targetContext
  private val manager =
      context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  @Before
  fun setUp() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      instrumentation.uiAutomation.grantRuntimePermission(
          context.packageName,
          Manifest.permission.POST_NOTIFICATIONS,
      )
    }
    manager.cancelAll()
  }

  @After
  fun tearDown() {
    manager.cancelAll()
  }

  @Test
  fun channelsExist_andANewSightingIsPosted() {
    val notifier = SystemNotifier(context)

    assertNotNull(manager.getNotificationChannel(SystemNotifier.CHANNEL_SIGHTINGS))
    assertNotNull(manager.getNotificationChannel(SystemNotifier.CHANNEL_NEARBY_ALERTS))
    assertTrue("Notifications should be enabled", manager.areNotificationsEnabled())

    notifier.notifySighting(
        SightingNotification(
            sightingId = "s1",
            alertId = "alert-1",
            petName = "Milo",
            sightingPhotoUrl = null,
            seenAtMillis = 100L,
        )
    )

    val posted = awaitPostedNotification()
    assertEquals("s1", posted.tag)
    assertEquals(SystemNotifier.CHANNEL_SIGHTINGS, posted.notification.channelId)
  }

  /** notify() returns before Android has posted the notification, so wait for it to appear. */
  private fun awaitPostedNotification(): StatusBarNotification {
    val deadline = SystemClock.uptimeMillis() + POST_TIMEOUT_MILLIS
    while (SystemClock.uptimeMillis() < deadline) {
      val active = manager.activeNotifications
      if (active.isNotEmpty()) return active.single()
      SystemClock.sleep(POLL_INTERVAL_MILLIS)
    }
    throw AssertionError("The notification was not posted within $POST_TIMEOUT_MILLIS ms")
  }

  private companion object {
    const val POST_TIMEOUT_MILLIS = 5_000L
    const val POLL_INTERVAL_MILLIS = 100L
  }
}
