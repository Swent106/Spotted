// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.trust

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.data.sighting.SightingRepositoryFirestore
import com.android.spotted.model.sighting.SightingStatus
import com.android.spotted.model.trust.SightingNotFoundException
import com.android.spotted.model.trust.SightingNotHiddenException
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
import org.junit.Assert.assertNull
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
  fun getTrust_readsTheFalseSightingsAndPenalties() = runTest {
    val helperId =
        seedTrust(score = doubleValue(0.3), judged = 6, falseSightings = 1, penaltiesApplied = 2)

    assertEquals(TrustScore(0.3, 6, 1, 2), repository.getTrust(helperId).getOrThrow())
  }

  @Test
  fun getTrust_withoutFalseCounts_hasNoFalseSighting() = runTest {
    val helperId = seedTrust(score = doubleValue(0.6), judged = 5)

    val trust = repository.getTrust(helperId).getOrThrow()

    assertEquals(0, trust.falseSightings)
    assertEquals(0, trust.penaltiesApplied)
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
    val withoutJudged = seedTrust(score = doubleValue(0.5), judged = null)
    val moreFalseThanJudged = seedTrust(score = doubleValue(0.5), judged = 1, falseSightings = 2)

    assertTrue(repository.getTrust(outOfRange).isFailure)
    assertTrue(repository.getTrust(withoutScore).isFailure)
    assertTrue(repository.getTrust(withoutJudged).isFailure)
    assertTrue(repository.getTrust(moreFalseThanJudged).isFailure)
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
    // Seeded: only the votes write a FALSE verdict (VoteRepositoryFirestore)
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

  // ===================== Restore (#463) =====================

  @Test
  fun restoreSighting_givesTheReporterTheirScoreBackAndSetsItPending() = runTest {
    // 0.6 and 5 judged before the penalty, then halved
    val helperId = seedTrust(score = doubleValue(0.3), judged = 6, falseSightings = 1)
    val sightingId = seedHiddenSighting(helperId, seedAlert(ownerId = currentUid()))

    repository.restoreSighting(sightingId).getOrThrow()

    val restored = repository.getTrust(helperId).getOrThrow()
    assertEquals(0.6, restored.score, 1e-9)
    assertEquals(TrustScore(restored.score, 5, 0, 1), restored)
    assertEquals("PENDING", sightingField(sightingId, "status"))
    assertEquals("FALSE", sightingField(sightingId, "trustVerdict"))
  }

  @Test
  fun restoreSighting_twice_failsTheSecondTimeAndChangesNothing() = runTest {
    val helperId = seedTrust(score = doubleValue(0.3), judged = 6, falseSightings = 1)
    val sightingId = seedHiddenSighting(helperId, seedAlert(ownerId = currentUid()))
    repository.restoreSighting(sightingId).getOrThrow()
    val afterRestore = repository.getTrust(helperId).getOrThrow()

    val error = repository.restoreSighting(sightingId).exceptionOrNull()

    assertTrue("was $error", error is SightingNotHiddenException)
    assertEquals(afterRestore, repository.getTrust(helperId).getOrThrow())
  }

  @Test
  fun restoreSighting_ofASightingNotHidden_failsWithSightingNotHidden() = runTest {
    val helperId = newHelperId()
    val sightingId = seedSighting(helperId, seedAlert(ownerId = currentUid()))

    val error = repository.restoreSighting(sightingId).exceptionOrNull()

    assertTrue("was $error", error is SightingNotHiddenException)
    assertEquals("PENDING", sightingField(sightingId, "status"))
  }

  @Test
  fun restoreSighting_ofAHiddenSightingAlreadyRestored_failsWithSightingNotHidden() = runTest {
    // Not reachable through the rules: a restored sighting is never hidden again
    val helperId = seedTrust(score = doubleValue(0.3), judged = 6, falseSightings = 1)
    val sightingId =
        seedSighting(
            helperId,
            seedAlert(ownerId = currentUid()),
            trustVerdict = "FALSE",
            status = "VOTED_FALSE",
            extraFields = mapOf("penaltyCancelled" to mapOf("booleanValue" to true)),
        )

    val error = repository.restoreSighting(sightingId).exceptionOrNull()

    assertTrue("was $error", error is SightingNotHiddenException)
    assertEquals(TrustScore(0.3, 6, 1, 1), repository.getTrust(helperId).getOrThrow())
  }

  @Test
  fun restoreSighting_ofAHiddenSightingWithoutItsSavedTrust_failsWithSightingNotHidden() = runTest {
    // Without the trust before the penalty, there is nothing to cancel it from
    val helperId = seedTrust(score = doubleValue(0.3), judged = 6, falseSightings = 1)
    val sightingId =
        seedSighting(
            helperId,
            seedAlert(ownerId = currentUid()),
            trustVerdict = "FALSE",
            status = "VOTED_FALSE",
        )

    val error = repository.restoreSighting(sightingId).exceptionOrNull()

    assertTrue("was $error", error is SightingNotHiddenException)
    assertEquals("VOTED_FALSE", sightingField(sightingId, "status"))
  }

  @Test
  fun restoreSighting_byAUserWhoDoesNotOwnTheAlert_changesNothing() = runTest {
    val helperId = seedTrust(score = doubleValue(0.3), judged = 6, falseSightings = 1)
    val sightingId = seedHiddenSighting(helperId, seedAlert(ownerId = "someone-else"))

    val error = repository.restoreSighting(sightingId).exceptionOrNull()

    assertEquals(
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        (error as? FirebaseFirestoreException)?.code,
    )
    assertEquals(TrustScore(0.3, 6, 1, 1), repository.getTrust(helperId).getOrThrow())
    assertEquals("VOTED_FALSE", sightingField(sightingId, "status"))
  }

  @Test
  fun setSightingStatus_ofAHiddenSighting_isRefused() = runTest {
    val helperId = seedTrust(score = doubleValue(0.3), judged = 6, falseSightings = 1)
    val sightingId = seedHiddenSighting(helperId, seedAlert(ownerId = currentUid()))

    // Leaving VOTED_FALSE without restoring would keep the reporter's penalty
    val result = sightingRepository.setSightingStatus(sightingId, SightingStatus.PENDING)

    assertTrue(result.isFailure)
    assertEquals("VOTED_FALSE", sightingField(sightingId, "status"))
    assertNull(sightingField(sightingId, "penaltyCancelled"))
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
      status: String = "PENDING",
      extraFields: Map<String, Any> = emptyMap(),
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
            "status" to stringValue(status),
            trustVerdict?.let { "trustVerdict" to stringValue(it) },
        ) + extraFields
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

  /**
   * Writes a sighting of [reporterId] hidden by the votes, bypassing the rules: the reporter had a
   * score of 0.6 and 5 judged sightings before its penalty.
   */
  private suspend fun seedHiddenSighting(reporterId: String, alertId: String): String {
    val beforePenalty =
        mapOf(
            "score" to doubleValue(0.6),
            "judged" to integerValue(5),
            "falseSightings" to integerValue(0),
            "penaltiesApplied" to integerValue(0),
        )
    return seedSighting(
        reporterId,
        alertId,
        trustVerdict = "FALSE",
        status = "VOTED_FALSE",
        extraFields =
            mapOf(
                "voteWeight" to doubleValue(2.0),
                "penalty" to mapOf("mapValue" to mapOf("fields" to beforePenalty)),
            ),
    )
  }

  private fun stringValue(value: String): Map<String, Any> = mapOf("stringValue" to value)

  private fun integerValue(value: Long): Map<String, Any> =
      mapOf("integerValue" to value.toString())

  private fun <K, V> mapOfNotNull(vararg pairs: Pair<K, V>?): Map<K, V> =
      pairs.filterNotNull().toMap()

  private fun doubleValue(value: Double): Map<String, Any> = mapOf("doubleValue" to value)

  /** Writes a trust document for a new uid, bypassing the rules (no client may write it). */
  private suspend fun seedTrust(
      score: Map<String, Any>?,
      judged: Long?,
      falseSightings: Long? = null,
      penaltiesApplied: Long? = falseSightings,
  ): String {
    val uid = "helper-${UUID.randomUUID()}"
    val fields = buildMap {
      score?.let { put("score", it) }
      judged?.let { put("judged", integerValue(it)) }
      falseSightings?.let { put("falseSightings", integerValue(it)) }
      penaltiesApplied?.let { put("penaltiesApplied", integerValue(it)) }
      if (falseSightings != null) put("lastSightingId", stringValue("a-judged-sighting"))
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
