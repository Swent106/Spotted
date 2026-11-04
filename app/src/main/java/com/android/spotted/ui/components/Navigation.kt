package com.android.spotted.ui.components

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController

/** True once this entry's screen is fully shown, i.e. no navigation transition is running. */
fun NavBackStackEntry.isResumed(): Boolean = lifecycle.currentState == Lifecycle.State.RESUMED

/**
 * Goes back one screen, ignoring the press while a transition is running or when the current screen
 * is the first one. A fast series of back presses then pops one screen at a time and never empties
 * the back stack (which would leave a blank screen).
 *
 * @return true if a screen was popped.
 */
fun NavController.popBackStackSafely(): Boolean {
  val current = currentBackStackEntry ?: return false
  if (!current.isResumed() || previousBackStackEntry == null) return false
  return popBackStack()
}
