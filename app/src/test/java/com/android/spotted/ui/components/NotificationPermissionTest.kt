// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.components

import android.Manifest
import android.app.Application
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** [RequestNotificationPermissionOnce], with a fake result registry instead of the dialog. */
@RunWith(AndroidJUnit4::class)
class NotificationPermissionTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val requests = mutableListOf<Any?>()
  private val registry =
      object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
          requests += input
        }
      }

  @Before
  @After
  fun resetSession() {
    NotificationPermissionSession.asked = false
  }

  private fun setContent() {
    composeTestRule.setContent {
      val owner =
          object : ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
          }
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
        RequestNotificationPermissionOnce()
      }
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun withoutThePermission_asksForIt_andRemembersItAsked() {
    setContent()

    assertEquals(listOf<Any?>(Manifest.permission.POST_NOTIFICATIONS), requests)
    assertEquals(true, NotificationPermissionSession.asked)
  }

  @Test
  fun whenAlreadyAskedThisLaunch_doesNotAskAgain() {
    NotificationPermissionSession.asked = true

    setContent()

    assertEquals(emptyList<Any?>(), requests)
  }

  @Test
  fun whenAlreadyGranted_doesNotAsk() {
    shadowOf(ApplicationProvider.getApplicationContext<Application>())
        .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

    setContent()

    assertEquals(emptyList<Any?>(), requests)
  }

  @Test
  @Config(sdk = [32])
  fun beforeAndroid13_neverAsks() {
    setContent()

    assertEquals(emptyList<Any?>(), requests)
  }
}
