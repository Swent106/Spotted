// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.fakes

import com.android.spotted.model.network.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [NetworkMonitor] for ViewModel tests: [online] is what [isOnline] returns, and can be changed;
 * each change is also sent to [onlineChanges] collectors.
 */
class FakeNetworkMonitor(online: Boolean = true) : NetworkMonitor {
  private val state = MutableStateFlow(online)

  var online: Boolean
    get() = state.value
    set(value) {
      state.value = value
    }

  override fun isOnline(): Boolean = online

  override val onlineChanges: Flow<Boolean> = state

  /** Whether someone is collecting [onlineChanges], i.e. the "platform" is being watched. */
  val isWatched: Boolean
    get() = state.subscriptionCount.value > 0
}
