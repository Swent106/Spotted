// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.android.spotted.model.network.NetworkMonitor

/**
 * [NetworkMonitor] backed by [ConnectivityManager]: online when the active network has internet and
 * Android has checked that it works (so a Wi-Fi behind a login page counts as offline).
 */
class NetworkMonitorAndroid(context: Context) : NetworkMonitor {
  private val connectivityManager =
      context.applicationContext.getSystemService(ConnectivityManager::class.java)

  override fun isOnline(): Boolean {
    val capabilities =
        connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
  }
}
