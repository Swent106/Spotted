package com.android.spotted.ui

enum class StartDestination {
  /** Not known yet: the signed-in user and their profile are still being read. */
  LOADING,
  SIGNIN,
  PERSONAL_INFO,
  HOME,
}
