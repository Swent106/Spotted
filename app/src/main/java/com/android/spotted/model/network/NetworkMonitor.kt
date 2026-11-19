// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.network

/** Tells whether the phone can reach the internet right now. */
interface NetworkMonitor {

  /** True when the phone has a working internet connection at the time of the call. */
  fun isOnline(): Boolean
}
