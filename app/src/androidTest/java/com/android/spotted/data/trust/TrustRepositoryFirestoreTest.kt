// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.trust

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.trust.SightingNotFoundException
import com.android.spotted.model.trust.TrustScore
import com.android.spotted.model.trust.Verdict
import com.android.spotted.model.trust.VerdictAlreadyAppliedException
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
  private val seededDocuments = mutableListOf<String>()

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
      seededDocuments.forEach { path -> emulatorRequest("DELETE", path) }
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

  @Test
  fun applyVerdict_confirmedOnAReporterWithNoTrust_startsFromInitial() = runTest {
    val helperId = newHelperId()
    val sightingId = seedSighting(helperId, seedAlert(ownerId = currentUid()))

    val next = repository.applyVerdict(sightingId, Verdict.CONFIRMED).getOrThrow()

    assertEquals(TrustScore.INITIAL.after(Verdict.CONFIRMED), next)
    assertEquals(next, repository.getTrust(helperId).getOrThrow())
  }

  @Test
  fun applyVerdict_false_halvesTheStoredScore() = runTest {
    val helperId = seedTrust(score = doubleValue(0.6), judged = 5)
    val sightingId = seedSighting(helperId, seedAlert(ownerId = currentUid()))

    repository.applyVerdict(sightingId, Verdict.FALSE).getOrThrow()

    assertEquals(TrustScore(0.3, 6), repository.getTrust(helperId).getOrThrow())
  }

  @Test
  fun applyVerdict_twiceOnTheSameSighting_failsAndKeepsTheFirst() = runTest {
    val helperId = newHelperId()
    val sightingId = seedSighting(helperId, seedAlert(ownerId = currentUid()))
    val afterFirst = repository.applyVerdict(sightingId, Verdict.CONFIRMED).getOrThrow()

    val error = repository.applyVerdict(sightingId, Verdict.FALSE).exceptionOrNull()

    assertTrue(error is VerdictAlreadyAppliedException)
    assertEquals(afterFirst, repository.getTrust(helperId).getOrThrow())
  }

  @Test
  fun applyVerdict_twoAtTheSameTime_areBothApplied() = runTest {
    val helperId = newHelperId()
    val alertId = seedAlert(ownerId = currentUid())
    val sightingIds = listOf(seedSighting(helperId, alertId), seedSighting(helperId, alertId))

    // Both transactions read the same trust: Firestore must retry one of them, not lose it.
    // Two confirmed verdicts give the same score in either order.
    val results = coroutineScope {
      sightingIds
          .map { sightingId -> async { repository.applyVerdict(sightingId, Verdict.CONFIRMED) } }
          .awaitAll()
    }

    results.forEach { it.getOrThrow() }
    assertEquals(
        TrustScore.INITIAL.after(Verdict.CONFIRMED).after(Verdict.CONFIRMED),
        repository.getTrust(helperId).getOrThrow(),
    )
  }

  @Test
  fun applyVerdict_byAUserWhoDoesNotOwnTheAlert_isDeniedAndChangesNothing() = runTest {
    val helperId = newHelperId()
    val sightingId = seedSighting(helperId, seedAlert(ownerId = "owner-${UUID.randomUUID()}"))

    val error = repository.applyVerdict(sightingId, Verdict.FALSE).exceptionOrNull()

    assertEquals(
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        (error as FirebaseFirestoreException).code,
    )
    assertEquals(TrustScore.INITIAL, repository.getTrust(helperId).getOrThrow())
  }

  @Test
  fun applyVerdict_ofAMissingSighting_failsWithSightingNotFound() = runTest {
    val error =
        repository
            .applyVerdict("sighting-${UUID.randomUUID()}", Verdict.CONFIRMED)
            .exceptionOrNull()

    assertTrue(error is SightingNotFoundException)
  }

  @Test
  fun trust_cannotBeWrittenOutsideApplyVerdict() = runTest {
    val helperId = seedTrust(score = doubleValue(0.3), judged = 0)

    val write = firestore.collection("trust").document(helperId).set(mapOf("score" to 1.0))
    // Tasks.await wraps the failure in an ExecutionException
    val error = runCatching { withContext(Dispatchers.IO) { Tasks.await(write) } }.exceptionOrNull()

    assertEquals(
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        (error?.cause as FirebaseFirestoreException).code,
    )
    assertEquals(TrustScore(0.3, 0), repository.getTrust(helperId).getOrThrow())
  }

  private fun currentUid(): String = checkNotNull(auth.currentUser).uid

  /** A new reporter uid; its trust document, if a test creates one, is deleted after the test. */
  private fun newHelperId(): String = "helper-${UUID.randomUUID()}".also { seededUids += it }

  /** Writes an open alert of [ownerId], bypassing the rules, and returns its id. */
  private suspend fun seedAlert(ownerId: String): String {
    val alertId = "alert-${UUID.randomUUID()}"
    val fields =
        mapOf(
            "id" to stringValue(alertId),
            "ownerId" to stringValue(ownerId),
            "status" to stringValue("OPEN"),
        )
    seedDocument("alerts", alertId, fields)
    return alertId
  }

  /** Writes a pending sighting of [reporterId] linked to [alertId], bypassing the rules. */
  private suspend fun seedSighting(reporterId: String, alertId: String): String {
    val sightingId = "sighting-${UUID.randomUUID()}"
    val location =
        mapOf(
            "mapValue" to
                mapOf(
                    "fields" to
                        mapOf("latitude" to doubleValue(46.5), "longitude" to doubleValue(6.6))
                )
        )
    val fields =
        mapOf(
            "id" to stringValue(sightingId),
            "reporterId" to stringValue(reporterId),
            "location" to location,
            "seenAtMillis" to mapOf("integerValue" to "1000000"),
            "species" to stringValue("DOG"),
            "alertId" to stringValue(alertId),
            "status" to stringValue("PENDING"),
        )
    seedDocument("sightings", sightingId, fields)
    return sightingId
  }

  private suspend fun seedDocument(collection: String, id: String, fields: Map<String, Any>) {
    val response =
        emulatorRequest(
            "POST",
            "$collection?documentId=$id",
            JSONObject(mapOf("fields" to fields)),
        )
    assertEquals(HttpURLConnection.HTTP_OK, response)
    seededDocuments += "$collection/$id"
  }

  private fun stringValue(value: String): Map<String, Any> = mapOf("stringValue" to value)

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
