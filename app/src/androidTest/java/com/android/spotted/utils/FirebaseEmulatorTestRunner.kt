// Portions of this code were generated with the help of Copilot.
package com.android.spotted.utils

import androidx.test.runner.AndroidJUnitRunner

class FirebaseEmulatorTestRunner : AndroidJUnitRunner() {
  override fun onStart() {
    FirebaseEmulators.configure()
    super.onStart()
  }
}
