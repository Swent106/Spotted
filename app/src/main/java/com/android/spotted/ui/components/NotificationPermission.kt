// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Remembers whether the notification permission was already asked since the app started. */
internal object NotificationPermissionSession {
  /** Set once the request is shown, so a refusal is respected until the next launch. */
  var asked: Boolean = false
}

/**
 * Asks for the notification permission at most once per app launch, and only if it isn't granted
 * yet. Android only has this permission since version 13. After a refusal, the app simply posts no
 * notification; the in-app ones still work.
 */
@Composable
fun RequestNotificationPermissionOnce() {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
  val context = LocalContext.current
  val launcher = rememberLauncherForActivityResult(RequestPermission()) {}
  LaunchedEffect(Unit) {
    val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    if (NotificationPermissionSession.asked || granted) return@LaunchedEffect
    NotificationPermissionSession.asked = true
    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
  }
}
