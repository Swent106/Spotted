// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.data.notification.SystemNotifier.Companion.EXTRA_ROUTE
import com.android.spotted.model.alert.ALERT_LINK_HOST
import com.android.spotted.model.alert.buildAlertLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** [routeOf] reads the screen a notification or an alert link asks for. */
@RunWith(AndroidJUnit4::class)
class RouteOfIntentTest {

  @Test
  fun anAlertLink_opensThatAlert() {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(buildAlertLink("a1").getOrThrow()))

    assertEquals(Route.alertDetail("a1"), routeOf(intent))
  }

  @Test
  fun aNotification_opensItsRoute() {
    val intent = Intent().putExtra(EXTRA_ROUTE, Route.myAlert("a1"))

    assertEquals(Route.myAlert("a1"), routeOf(intent))
  }

  @Test
  fun theLauncherOrAnotherLink_opensNothing() {
    assertNull(routeOf(Intent(Intent.ACTION_MAIN)))
    assertNull(routeOf(Intent(Intent.ACTION_VIEW, Uri.parse("https://$ALERT_LINK_HOST/pet/a1"))))
  }

  @Test
  fun aLinkReopenedFromRecents_opensNothing() {
    val intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(buildAlertLink("a1").getOrThrow()))
            .addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

    assertNull(routeOf(intent))
  }

  @Test
  fun aNotificationForAnotherScreen_opensNothing() {
    assertNull(routeOf(Intent().putExtra(EXTRA_ROUTE, Route.EDIT_PROFILE)))
  }
}
