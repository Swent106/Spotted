// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.data.notification.SystemNotifier.Companion.EXTRA_ROUTE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** [routeOf] reads the screen a notification or an alert link asks for. */
@RunWith(AndroidJUnit4::class)
class RouteOfIntentTest {

  @Test
  fun anAlertLink_opensThatAlert() {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spotted://alert/a1"))

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
    assertNull(routeOf(Intent(Intent.ACTION_VIEW, Uri.parse("spotted://pet/a1"))))
  }
}
