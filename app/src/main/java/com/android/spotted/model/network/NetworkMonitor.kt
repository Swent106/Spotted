// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Tells whether the phone can reach the internet right now. */
interface NetworkMonitor {

  /** True when the phone has a working internet connection at the time of the call. */
  fun isOnline(): Boolean

  /**
   * Whether the phone is online: the current value first, then each change. The platform is only
   * watched while this flow is collected, so stop collecting when the app is in the background.
   *
   * By default it only reports [isOnline] once; implementations that can watch the platform
   * override it.
   */
  val onlineChanges: Flow<Boolean>
    get() = flow { emit(isOnline()) }
}
