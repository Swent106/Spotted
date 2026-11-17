// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.trust

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.trust.TrustScore
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrustRepositoryFirestoreTest {
  private lateinit var auth: FirebaseAuth
  private lateinit var firestore: FirebaseFirestore
  private lateinit var repository: TrustRepositoryFirestore
  private val seededUids = mutableListOf<String>()

  @Before
  fun setUp() = runTest {
    withContext(Dispatchers.IO) {
      auth = FirebaseAuth.getInstance()
      firestore = FirebaseFirestore.getInstance()
      repository = TrustRepositoryFirestore(firestore)
      Tasks.await(auth.signInAnonymously())
    }
  }

  @After
  fun cleanUp() = runTest {
    withContext(Dispatchers.IO) {
      seededUids.forEach { uid -> emulatorRequest("DELETE", "trust/$uid") }
      auth.signOut()
    }
  }

  @Test
  fun getTrust_ofAnotherUser_returnsTheStoredTrust() = runTest {
    val helperId = seedTrust(score = doubleValue(0.6), judged = 5)

    assertEquals(TrustScore(0.6, 5), repository.getTrust(helperId).getOrThrow())
  }

  @Test
  fun getTrust_withAWholeNumberScore_readsItAsADouble() = runTest {
    // A writer may store 1 instead of 1.0; Firestore then keeps it as an integer
    val helperId = seedTrust(score = mapOf("integerValue" to "1"), judged = 12)

    assertEquals(TrustScore(1.0, 12), repository.getTrust(helperId).getOrThrow())
  }

  @Test
  fun getTrust_ofAUserWithNoDocument_isInitial() = runTest {
    val helperId = "helper-${UUID.randomUUID()}"

    assertEquals(TrustScore.INITIAL, repository.getTrust(helperId).getOrThrow())
  }

  @Test
  fun getTrust_ofAnInvalidDocument_fails() = runTest {
    val outOfRange = seedTrust(score = doubleValue(1.5), judged = 3)
    val withoutScore = seedTrust(score = null, judged = 3)

    assertTrue(repository.getTrust(outOfRange).isFailure)
    assertTrue(repository.getTrust(withoutScore).isFailure)
  }

  @Test
  fun getTrust_whenSignedOut_failsWithPermissionDenied() = runTest {
    val helperId = seedTrust(score = doubleValue(0.6), judged = 5)
    auth.signOut()

    val error = repository.getTrust(helperId).exceptionOrNull()

    assertEquals(
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        (error as FirebaseFirestoreException).code,
    )
  }

  private fun doubleValue(value: Double): Map<String, Any> = mapOf("doubleValue" to value)

  /** Writes a trust document for a new uid, bypassing the rules (no client may write it). */
  private suspend fun seedTrust(score: Map<String, Any>?, judged: Long): String {
    val uid = "helper-${UUID.randomUUID()}"
    val fields = buildMap {
      score?.let { put("score", it) }
      put("judged", mapOf("integerValue" to judged.toString()))
    }
    val response =
        emulatorRequest("POST", "trust?documentId=$uid", JSONObject(mapOf("fields" to fields)))
    assertEquals(HttpURLConnection.HTTP_OK, response)
    seededUids += uid
    return uid
  }

  private suspend fun emulatorRequest(method: String, path: String, body: JSONObject? = null): Int {
    val url =
        URL(
            "http://${firestore.firestoreSettings.host}/v1/projects/${firestore.app.options.projectId}" +
                "/databases/(default)/documents/$path"
        )
    return withContext(Dispatchers.IO) {
      val connection = url.openConnection() as HttpURLConnection
      try {
        connection.requestMethod = method
        connection.setRequestProperty("Authorization", "Bearer owner")
        if (body != null) {
          connection.doOutput = true
          connection.setRequestProperty("Content-Type", "application/json")
          connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
        }
        connection.responseCode
      } finally {
        connection.disconnect()
      }
    }
  }
}
