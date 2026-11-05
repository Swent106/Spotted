package com.android.spotted.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Remembers whether the location permission was already asked since the app started. */
internal object LocationPermissionSession {
  /** Set once the request is shown, so a refusal is respected until the next launch. */
  @Volatile var asked: Boolean = false
}

private val LOCATION_PERMISSIONS =
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/**
 * Asks for the location permission (precise or approximate) when the screen opens, at most once per
 * app launch and only if it isn't granted yet. [onGranted] is called when the user grants it, so
 * the screen can reload with the GPS position; after a refusal nothing happens and the screen keeps
 * its fallback (the home area).
 */
@Composable
fun RequestLocationPermissionOnce(onGranted: () -> Unit) {
  val context = LocalContext.current
  val currentOnGranted by rememberUpdatedState(onGranted)
  val launcher =
      rememberLauncherForActivityResult(RequestMultiplePermissions()) { results ->
        if (results.values.any { it }) currentOnGranted()
      }
  LaunchedEffect(Unit) {
    if (LocationPermissionSession.asked || hasLocationPermission(context)) return@LaunchedEffect
    LocationPermissionSession.asked = true
    launcher.launch(LOCATION_PERMISSIONS)
  }
}

private fun hasLocationPermission(context: Context): Boolean = LOCATION_PERMISSIONS.any {
  ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}
