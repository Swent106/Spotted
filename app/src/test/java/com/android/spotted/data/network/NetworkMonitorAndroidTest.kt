// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.network

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NetworkMonitorAndroidTest {

  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
  private val network: Network = connectivityManager.activeNetwork!!
  private val monitor = NetworkMonitorAndroid(context)

  private val online =
      capabilities(
          NetworkCapabilities.NET_CAPABILITY_INTERNET,
          NetworkCapabilities.NET_CAPABILITY_VALIDATED,
      )

  /** Internet but not validated by Android, e.g. a Wi-Fi behind a login page. */
  private val notValidated = capabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET)

  private fun capabilities(vararg capabilities: Int): NetworkCapabilities =
      ShadowNetworkCapabilities.newInstance().also { caps ->
        capabilities.forEach { shadowOf(caps).addCapability(it) }
      }

  private fun setActiveCapabilities(capabilities: NetworkCapabilities) {
    shadowOf(connectivityManager).setNetworkCapabilities(network, capabilities)
  }

  private val callbacks
    get() = shadowOf(connectivityManager).networkCallbacks

  /** Collects [NetworkMonitorAndroid.onlineChanges] into [values] until the job is cancelled. */
  private fun TestScope.collect(values: MutableList<Boolean>): Job =
      launch(UnconfinedTestDispatcher(testScheduler)) { monitor.onlineChanges.toList(values) }

  @Test
  fun isOnline_isTrue_withInternetValidatedByAndroid() {
    setActiveCapabilities(online)

    assertTrue(monitor.isOnline())
  }

  @Test
  fun isOnline_isFalse_withInternetNotValidated() {
    setActiveCapabilities(notValidated)

    assertFalse(monitor.isOnline())
  }

  @Test
  fun onlineChanges_startsWithTheCurrentState() = runTest {
    setActiveCapabilities(notValidated)
    val values = mutableListOf<Boolean>()

    val job = collect(values)

    assertEquals(listOf(false), values)
    job.cancelAndJoin()
  }

  @Test
  fun onlineChanges_followsCapabilitiesAndLostNetwork() = runTest {
    setActiveCapabilities(online)
    val values = mutableListOf<Boolean>()
    val job = collect(values)
    val callback = callbacks.single()

    callback.onCapabilitiesChanged(network, online) // same state: not repeated
    callback.onCapabilitiesChanged(network, notValidated)
    callback.onCapabilitiesChanged(network, online)
    callback.onLost(network)

    assertEquals(listOf(true, false, true, false), values)
    job.cancelAndJoin()
  }

  @Test
  fun onlineChanges_unregistersTheCallback_whenCollectionStops() = runTest {
    setActiveCapabilities(online)
    val job = collect(mutableListOf())
    assertEquals(1, callbacks.size)

    job.cancelAndJoin()

    assertTrue(callbacks.isEmpty())
  }
}
