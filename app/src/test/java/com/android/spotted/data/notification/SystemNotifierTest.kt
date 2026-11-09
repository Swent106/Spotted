// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.notification

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.android.spotted.Route
import com.android.spotted.model.notification.SightingNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SystemNotifierTest {

  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val manager =
      context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  private val notifier = SystemNotifier(context)

  private fun routeOpenedBy(notification: Notification) =
      shadowOf(notification.contentIntent).savedIntent.getStringExtra(SystemNotifier.EXTRA_ROUTE)

  private fun posted(sightingId: String): Notification? =
      shadowOf(manager).getNotification(sightingId, SystemNotifier.SIGHTING_NOTIFICATION_ID)

  @Test
  fun creatingIt_registersBothChannels() {
    assertNotNull(manager.getNotificationChannel(SystemNotifier.CHANNEL_SIGHTINGS))
    assertNotNull(manager.getNotificationChannel(SystemNotifier.CHANNEL_NEARBY_ALERTS))
  }

  @Test
  fun notifySighting_namesThePet_andOpensMyAlertWhenTapped() {
    notifier.notifySighting(sighting("s1", "alert-1"))

    val notification = posted("s1")!!
    assertEquals(SystemNotifier.CHANNEL_SIGHTINGS, notification.channelId)
    assertEquals("Someone saw Milo", shadowOf(notification).contentTitle)
    assertEquals(Route.myAlert("alert-1"), routeOpenedBy(notification))
    assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
  }

  @Test
  fun eachSighting_getsItsOwnNotification_openingItsOwnAlert() {
    notifier.notifySighting(sighting("s1", "alert-1"))
    notifier.notifySighting(sighting("s2", "alert-2"))

    assertEquals(2, shadowOf(manager).size())
    assertEquals(Route.myAlert("alert-1"), routeOpenedBy(posted("s1")!!))
    assertEquals(Route.myAlert("alert-2"), routeOpenedBy(posted("s2")!!))
    assertNotEquals(posted("s1")!!.contentIntent, posted("s2")!!.contentIntent)
  }

  @Test
  fun whenNotificationsAreRefused_postsNothing() {
    shadowOf(manager).setNotificationsEnabled(false)

    notifier.notifySighting(sighting("s1", "alert-1"))

    assertEquals(0, shadowOf(manager).size())
  }

  private fun sighting(sightingId: String, alertId: String) =
      SightingNotification(
          sightingId = sightingId,
          alertId = alertId,
          petName = "Milo",
          sightingPhotoUrl = null,
          seenAtMillis = 100L,
      )
}
