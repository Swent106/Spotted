// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.fakes

import com.android.spotted.model.network.NetworkMonitor

/**
 * [NetworkMonitor] for ViewModel tests: [online] is what [isOnline] returns, and can be changed.
 */
class FakeNetworkMonitor(var online: Boolean = true) : NetworkMonitor {
  override fun isOnline(): Boolean = online
}
