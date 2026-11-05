package com.android.spotted.ui.components

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * [RequestLocationPermissionOnce] without the system dialog: the permission state comes from a fake
 * context, and the user's answer from a fake result registry.
 */
class LocationPermissionTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Before
  fun resetSession() {
    LocationPermissionSession.asked = false
  }

  @After
  fun clearSession() {
    LocationPermissionSession.asked = false
  }

  @Test
  fun withoutPermission_asksForBoth_andCallsOnGrantedWhenAllowed() {
    val registry = AnsweringRegistry(allow = true)
    var granted = 0

    setContent(hasPermission = false, registry) { RequestLocationPermissionOnce { granted++ } }

    assertEquals(
        listOf(
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        ),
        registry.requests,
    )
    assertEquals(1, granted)
  }

  @Test
  fun refusal_doesNotCallOnGranted() {
    val registry = AnsweringRegistry(allow = false)
    var granted = 0

    setContent(hasPermission = false, registry) { RequestLocationPermissionOnce { granted++ } }

    assertEquals(1, registry.requests.size)
    assertEquals(0, granted)
  }

  @Test
  fun twoScreens_askOnlyOncePerLaunch() {
    val registry = AnsweringRegistry(allow = false)

    setContent(hasPermission = false, registry) {
      RequestLocationPermissionOnce {}
      RequestLocationPermissionOnce {}
    }

    assertEquals(1, registry.requests.size)
  }

  @Test
  fun alreadyGranted_doesNotAsk() {
    val registry = AnsweringRegistry(allow = true)
    var granted = 0

    setContent(hasPermission = true, registry) { RequestLocationPermissionOnce { granted++ } }

    assertEquals(0, registry.requests.size)
    assertEquals(0, granted)
  }

  private fun setContent(
      hasPermission: Boolean,
      registry: ActivityResultRegistry,
      content: @androidx.compose.runtime.Composable () -> Unit,
  ) {
    val context =
        PermissionContext(InstrumentationRegistry.getInstrumentation().targetContext, hasPermission)
    val owner =
        object : ActivityResultRegistryOwner {
          override val activityResultRegistry = registry
        }
    composeTestRule.setContent {
      CompositionLocalProvider(
          LocalContext provides context,
          LocalActivityResultRegistryOwner provides owner,
          content = content,
      )
    }
    composeTestRule.waitForIdle()
  }

  /** Answers every permission check with [granted]. */
  private class PermissionContext(base: Context, private val granted: Boolean) :
      ContextWrapper(base) {
    override fun checkPermission(permission: String, pid: Int, uid: Int) =
        if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
  }

  /** Records each permission request and answers it at once, as if the user tapped [allow]. */
  private class AnsweringRegistry(private val allow: Boolean) : ActivityResultRegistry() {
    val requests = mutableListOf<List<String>>()

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
      val permissions = (input as Array<*>).map { it as String }
      requests += permissions
      dispatchResult(requestCode, permissions.associateWith { allow })
    }
  }
}
