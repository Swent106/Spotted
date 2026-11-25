// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.android.spotted.model.network.NetworkMonitor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * [NetworkMonitor] backed by [ConnectivityManager]: online when the active network has internet and
 * Android has checked that it works (so a Wi-Fi behind a login page counts as offline).
 */
class NetworkMonitorAndroid(context: Context) : NetworkMonitor {
  private val connectivityManager =
      context.applicationContext.getSystemService(ConnectivityManager::class.java)

  override fun isOnline(): Boolean =
      connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork).hasInternet()

  /**
   * Listens to Android's default network callback (no network request is made). The callback is
   * registered when collection starts and unregistered when it stops.
   */
  override val onlineChanges: Flow<Boolean>
    get() = callbackFlow {
      val callback =
          object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
              trySend(capabilities.hasInternet())
            }

            override fun onLost(network: Network) {
              trySend(false)
            }
          }
      // The callback is only called on changes, so start with the current state
      trySend(isOnline())
      connectivityManager.registerDefaultNetworkCallback(callback)
      awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }
        .distinctUntilChanged()

  private fun NetworkCapabilities?.hasInternet(): Boolean =
      this != null &&
          hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
          hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
