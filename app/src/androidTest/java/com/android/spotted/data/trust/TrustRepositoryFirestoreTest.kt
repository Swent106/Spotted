// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.trust

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.data.sighting.SightingRepositoryFirestore
import com.android.spotted.model.sighting.SightingStatus
import com.android.spotted.model.trust.SightingNotFoundException
import com.android.spotted.model.trust.TrustScore
import com.android.spotted.model.trust.Verdict
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
  // The app's path for dismissing (and re-confirming) a sighting: a status-only write
  private lateinit var sightingRepository: SightingRepositoryFirestore
  private val seededUids = mutableListOf<String>()
  private val seededDocuments = mutableListOf<String>()

  @Before
  fun setUp() = runTest {
    withContext(Dispatchers.IO) {
      auth = FirebaseAuth.getInstance()
      firestore = FirebaseFirestore.getInstance()
      repository = TrustRepositoryFirestore(firestore)
      sightingRepository = SightingRepositoryFirestore(firestore)
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
  fun confirmSighting_ofAReporterWithNoTrust_startsFromInitial() = runTest {
    val helperId = newHelperId()
    val sightingId = seedSighting(helperId, seedAlert(ownerId = currentUid()))

    repository.confirmSighting(sightingId).getOrThrow()

    assertEquals(
        TrustScore.INITIAL.after(Verdict.CONFIRMED),
        repository.getTrust(helperId).getOrThrow(),
    )
    assertEquals("CONFIRMED", sightingField(sightingId, "status"))
    assertEquals("CONFIRMED", sightingField(sightingId, "trustVerdict"))
  }

  @Test
  fun confirmSighting_twoSightingsAtTheSameTime_areBothCounted() = runTest {
    val helperId = newHelperId()
    val alertId = seedAlert(ownerId = currentUid())
    val sightingIds = listOf(seedSighting(helperId, alertId), seedSighting(helperId, alertId))

    // Both transactions read the same trust: the stale one is refused by the rules and run again
    val results = coroutineScope {
      sightingIds.map { sightingId -> async { repository.confirmSighting(sightingId) } }.awaitAll()
    }

    results.forEach { it.getOrThrow() }
    assertEquals(
        TrustScore.INITIAL.after(Verdict.CONFIRMED).after(Verdict.CONFIRMED),
        repository.getTrust(helperId).getOrThrow(),
    )
  }

  @Test
  fun trust_cannotBeWrittenOutsideConfirmSighting() = runTest {
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

  @Test
  fun confirmSighting_confirmedDismissedConfirmed_updatesTheScoreOnce() = runTest {
    val helperId = newHelperId()
    val sightingId = seedSighting(helperId, seedAlert(ownerId = currentUid()))

    repository.confirmSighting(sightingId).getOrThrow()
    sightingRepository.setSightingStatus(sightingId, SightingStatus.DISMISSED).getOrThrow()
    repository.confirmSighting(sightingId).getOrThrow()

    assertEquals(
        TrustScore.INITIAL.after(Verdict.CONFIRMED),
        repository.getTrust(helperId).getOrThrow(),
    )
    assertEquals("CONFIRMED", sightingField(sightingId, "status"))
    assertEquals("CONFIRMED", sightingField(sightingId, "trustVerdict"))
  }

  @Test
  fun confirmSighting_twoAtTheSameTime_updateTheScoreOnce() = runTest {
    val helperId = newHelperId()
    val sightingId = seedSighting(helperId, seedAlert(ownerId = currentUid()))

    // The second transaction sees the sighting judged by the first, and only sets the status
    val results = coroutineScope {
      List(2) { async { repository.confirmSighting(sightingId) } }.awaitAll()
    }

    results.forEach { it.getOrThrow() }
    assertEquals(
        TrustScore.INITIAL.after(Verdict.CONFIRMED),
        repository.getTrust(helperId).getOrThrow(),
    )
  }

  @Test
  fun dismissingASighting_doesNotChangeTheScore() = runTest {
    val helperId = seedTrust(score = doubleValue(0.6), judged = 5)
    val sightingId = seedSighting(helperId, seedAlert(ownerId = currentUid()))

    sightingRepository.setSightingStatus(sightingId, SightingStatus.DISMISSED).getOrThrow()

    assertEquals(TrustScore(0.6, 5), repository.getTrust(helperId).getOrThrow())
    assertEquals(null, sightingField(sightingId, "trustVerdict"))
  }

  @Test
  fun confirmSighting_alreadyJudgedFalse_onlyChangesTheStatus() = runTest {
    // No client can write a FALSE verdict in S6: seeded, as the votes of US-T2 will write it
    val helperId = seedTrust(score = doubleValue(0.15), judged = 1)
    val sightingId =
        seedSighting(helperId, seedAlert(ownerId = currentUid()), trustVerdict = "FALSE")

    repository.confirmSighting(sightingId).getOrThrow()

    assertEquals(TrustScore(0.15, 1), repository.getTrust(helperId).getOrThrow())
    assertEquals("CONFIRMED", sightingField(sightingId, "status"))
    assertEquals("FALSE", sightingField(sightingId, "trustVerdict"))
  }

  @Test
  fun confirmSighting_byAUserWhoDoesNotOwnTheAlert_changesNeitherStatusNorScore() = runTest {
    val helperId = newHelperId()
    val sightingId = seedSighting(helperId, seedAlert(ownerId = "owner-${UUID.randomUUID()}"))

    val error = repository.confirmSighting(sightingId).exceptionOrNull()

    assertEquals(
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        (error as FirebaseFirestoreException).code,
    )
    assertEquals(TrustScore.INITIAL, repository.getTrust(helperId).getOrThrow())
    assertEquals("PENDING", sightingField(sightingId, "status"))
    assertEquals(null, sightingField(sightingId, "trustVerdict"))
  }

  @Test
  fun confirmSighting_whenTheTransactionFails_changesNeitherStatusNorScore() = runTest {
    // A trust without a score cannot be read, so the transaction throws before it writes
    val helperId = seedTrust(score = null, judged = 3)
    val sightingId = seedSighting(helperId, seedAlert(ownerId = currentUid()))

    assertTrue(repository.confirmSighting(sightingId).isFailure)

    assertEquals("PENDING", sightingField(sightingId, "status"))
    assertEquals(null, sightingField(sightingId, "trustVerdict"))
    assertTrue(repository.getTrust(helperId).isFailure)
  }

  @Test
  fun confirmSighting_ofASightingWithoutReporter_failsAndChangesNothing() = runTest {
    val sightingId = seedSighting(reporterId = null, alertId = seedAlert(ownerId = currentUid()))

    assertTrue(repository.confirmSighting(sightingId).exceptionOrNull() is IllegalStateException)

    assertEquals("PENDING", sightingField(sightingId, "status"))
    assertEquals(null, sightingField(sightingId, "trustVerdict"))
  }

  @Test
  fun confirmSighting_ofAMissingSighting_failsWithSightingNotFound() = runTest {
    val error = repository.confirmSighting("sighting-${UUID.randomUUID()}").exceptionOrNull()

    assertTrue(error is SightingNotFoundException)
  }

  @Test
  fun setSightingStatus_confirmed_isRefusedAndChangesNothing() = runTest {
    val sightingId = seedSighting(newHelperId(), seedAlert(ownerId = currentUid()))

    val error =
        sightingRepository.setSightingStatus(sightingId, SightingStatus.CONFIRMED).exceptionOrNull()

    assertTrue(error is IllegalArgumentException)
    assertEquals("PENDING", sightingField(sightingId, "status"))
  }

  @Test
  fun firstConfirmation_withoutItsVerdict_isDeniedByTheRules() = runTest {
    val sightingId = seedSighting(newHelperId(), seedAlert(ownerId = currentUid()))

    val write = firestore.collection("sightings").document(sightingId).update("status", "CONFIRMED")
    // Tasks.await wraps the failure in an ExecutionException
    val error = runCatching { withContext(Dispatchers.IO) { Tasks.await(write) } }.exceptionOrNull()

    assertEquals(
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        (error?.cause as FirebaseFirestoreException).code,
    )
    assertEquals("PENDING", sightingField(sightingId, "status"))
  }

  private fun currentUid(): String = checkNotNull(auth.currentUser).uid

  /** The string [field] of the sighting [sightingId] as stored, or null if it has none. */
  private suspend fun sightingField(sightingId: String, field: String): String? =
      withContext(Dispatchers.IO) {
        Tasks.await(firestore.collection("sightings").document(sightingId).get()).getString(field)
      }

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

  /**
   * Writes a pending sighting of [reporterId] linked to [alertId], bypassing the rules. A null
   * [reporterId] writes a broken sighting without one; a [trustVerdict] writes it already judged.
   */
  private suspend fun seedSighting(
      reporterId: String?,
      alertId: String,
      trustVerdict: String? = null,
  ): String {
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
        mapOfNotNull(
            "id" to stringValue(sightingId),
            reporterId?.let { "reporterId" to stringValue(it) },
            "location" to location,
            "seenAtMillis" to mapOf("integerValue" to "1000000"),
            "species" to stringValue("DOG"),
            "alertId" to stringValue(alertId),
            "status" to stringValue("PENDING"),
            trustVerdict?.let { "trustVerdict" to stringValue(it) },
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

  private fun <K, V> mapOfNotNull(vararg pairs: Pair<K, V>?): Map<K, V> =
      pairs.filterNotNull().toMap()

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
