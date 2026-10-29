// Portions of this code were generated with the help of Copilot.
package com.android.spotted.utils

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

object FirebaseEmulators {
  private const val HOST = "10.0.2.2"
  private const val AUTH_PORT = 9099
  private const val FIRESTORE_PORT = 8080
  private const val STORAGE_PORT = 9199
  private const val READINESS_TIMEOUT_SECONDS = 30L

  private var configured = false

  @Synchronized
  fun configure() {
    if (configured) return

    FirebaseAuth.getInstance().useEmulator(HOST, AUTH_PORT)
    FirebaseFirestore.getInstance().useEmulator(HOST, FIRESTORE_PORT)
    FirebaseStorage.getInstance().useEmulator(HOST, STORAGE_PORT)
    awaitFirestore()
    configured = true
  }

  private fun awaitFirestore() {
    try {
      Tasks.await(
          FirebaseFirestore.getInstance()
              .collection("emulatorReadinessProbe")
              .limit(1)
              .get(Source.SERVER),
          READINESS_TIMEOUT_SECONDS,
          TimeUnit.SECONDS,
      )
    } catch (exception: ExecutionException) {
      val firestoreException = exception.cause as? FirebaseFirestoreException
      // The default-deny rules reject the probe once the emulator responds.
      if (firestoreException?.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        throw exception.cause ?: exception
      }
    }
  }
}
