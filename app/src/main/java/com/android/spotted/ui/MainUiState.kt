// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui

/** App-wide state: where the app starts, and whether the phone is online. */
data class MainUiState(
    val destination: StartDestination = StartDestination.LOADING,
    /** Starts true so nothing shows "offline" before the first real value arrives. */
    val isOnline: Boolean = true,
)

enum class StartDestination {
  /** Not known yet: the signed-in user and their profile are still being read. */
  LOADING,
  SIGNIN,
  PERSONAL_INFO,
  HOME,
}
