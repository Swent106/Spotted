package com.android.spotted.utils

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseEmulators {
  private const val HOST = "10.0.2.2"
  private const val AUTH_PORT = 9099
  private const val FIRESTORE_PORT = 8080

  private var configured = false

  @Synchronized
  fun configure() {
    if (configured) return

    FirebaseAuth.getInstance().useEmulator(HOST, AUTH_PORT)
    FirebaseFirestore.getInstance().useEmulator(HOST, FIRESTORE_PORT)
    configured = true
  }
}
