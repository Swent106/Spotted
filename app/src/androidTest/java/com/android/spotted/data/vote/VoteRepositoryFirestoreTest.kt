// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.vote

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.data.trust.TrustRepositoryFirestore
import com.android.spotted.model.trust.TrustScore
import com.android.spotted.model.trust.Verdict
import com.android.spotted.model.vote.AlreadyVotedException
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [VoteRepositoryFirestore] against the Firestore emulator and its rules. The signed-in user is the
 * voter: their account is 30 days old and Trusted, so the rules let them vote on other users'
 * sightings.
 */
@RunWith(AndroidJUnit4::class)
class VoteRepositoryFirestoreTest {
  private lateinit var auth: FirebaseAuth
  private lateinit var firestore: FirebaseFirestore
  private lateinit var repository: VoteRepositoryFirestore
  private val seededDocuments = mutableListOf<String>()

  @Before
  fun setUp() = runTest {
    withContext(Dispatchers.IO) {
      auth = FirebaseAuth.getInstance()
      firestore = FirebaseFirestore.getInstance()
      repository = VoteRepositoryFirestore(firestore)
      Tasks.await(auth.signInAnonymously())
    }
  }

  @After
  fun cleanUp() = runTest {
    withContext(Dispatchers.IO) {
      seededDocuments.forEach { path -> emulatorRequest("DELETE", path) }
      emulatorRequest("DELETE", "voteQuota/${voterId()}")
      auth.signOut()
    }
  }

  @Test
  fun castVote_eligibleVoter_storesTheVote() = runTest {
    seedEligibleVoter()
    val sightingId = seedSighting()
    assertFalse(repository.hasVoted(sightingId, voterId()).getOrThrow())

    assertTrue(repository.castVote(sightingId, voterId()).isSuccess)

    assertEquals(1, repository.getVoteCount(sightingId).getOrThrow())
    assertTrue(repository.hasVoted(sightingId, voterId()).getOrThrow())
  }

  @Test
  fun castVote_secondVote_failsWithAlreadyVotedAndCountStaysOne() = runTest {
    seedEligibleVoter()
    val sightingId = seedSighting()
    repository.castVote(sightingId, voterId()).getOrThrow()

    val error = repository.castVote(sightingId, voterId()).exceptionOrNull()

    assertTrue("was $error", error is AlreadyVotedException)
    assertEquals(1, repository.getVoteCount(sightingId).getOrThrow())
  }

  // Who may vote is tested in the rules tests (firestore.rules.test.js); here, the repository's own
  // logic: the transaction, the AlreadyVotedException, the quota it writes, and the reads

  @Test
  fun castVote_tenVotesInADay_theEleventhIsRefused() = runTest {
    seedEligibleVoter()
    val sightingIds = List(11) { seedSighting() }

    sightingIds.take(10).forEach { repository.castVote(it, voterId()).getOrThrow() }
    val eleventh = repository.castVote(sightingIds.last(), voterId())

    assertPermissionDenied(eleventh.exceptionOrNull())
    assertFalse(repository.hasVoted(sightingIds.last(), voterId()).getOrThrow())
  }

  // ===================== Weight and threshold (#463) =====================

  @Test
  fun castVote_addsTheVotersScoreToTheWeight() = runTest {
    seedEligibleVoter()
    val sightingId = seedSighting(voteWeight = 1.0)

    repository.castVote(sightingId, voterId()).getOrThrow()

    val sighting = sightingSnapshot(sightingId)
    assertEquals(1.5, sighting.getDouble("voteWeight")!!, 1e-9)
    assertEquals("PENDING", sighting.getString("status"))
  }

  @Test
  fun castVote_reachingTheThreshold_hidesTheSightingAndPenalisesTheReporter() = runTest {
    seedEligibleVoter()
    val sightingId = seedSighting(voteWeight = 1.5)

    repository.castVote(sightingId, voterId()).getOrThrow()

    val sighting = sightingSnapshot(sightingId)
    assertEquals("VOTED_FALSE", sighting.getString("status"))
    assertEquals("FALSE", sighting.getString("trustVerdict"))
    assertEquals(2.0, sighting.getDouble("voteWeight")!!, 1e-9)
    assertEquals(
        TrustScore.INITIAL.after(Verdict.FALSE),
        trustRepository().getTrust(sighting.getString("reporterId")!!).getOrThrow(),
    )
  }

  @Test
  fun castVote_onAConfirmedSighting_onlyAddsWeight() = runTest {
    seedEligibleVoter()
    val sightingId =
        seedSighting(voteWeight = 1.5, status = "CONFIRMED", trustVerdict = "CONFIRMED")

    repository.castVote(sightingId, voterId()).getOrThrow()

    val sighting = sightingSnapshot(sightingId)
    assertEquals("CONFIRMED", sighting.getString("status"))
    assertEquals(2.0, sighting.getDouble("voteWeight")!!, 1e-9)
    assertEquals(
        TrustScore.INITIAL,
        trustRepository().getTrust(sighting.getString("reporterId")!!).getOrThrow(),
    )
  }

  @Test
  fun getVoteCount_sightingWithoutVotes_isZero() = runTest {
    val sightingId = seedSighting()

    assertEquals(0, repository.getVoteCount(sightingId).getOrThrow())
  }

  private fun assertPermissionDenied(error: Throwable?) {
    assertTrue(
        "expected PERMISSION_DENIED, was $error",
        (error as? FirebaseFirestoreException)?.code ==
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
    )
  }

  private fun trustRepository() = TrustRepositoryFirestore(firestore)

  private suspend fun sightingSnapshot(sightingId: String) =
      withContext(Dispatchers.IO) {
        Tasks.await(firestore.collection("sightings").document(sightingId).get())
      }

  private fun voterId(): String =
      requireNotNull(auth.currentUser) { "Test user was not authenticated" }.uid

  /** Writes the voter's profile and trust score, bypassing the rules. */
  private suspend fun seedEligibleVoter() {
    val createdAt = Instant.now().minus(Duration.ofDays(30)).toString()
    seedDocument(
        "users",
        voterId(),
        mapOf(
            "uid" to stringValue(voterId()),
            "name" to stringValue("Voter"),
            "phone" to stringValue(""),
            "email" to stringValue("voter@example.com"),
            "homeArea" to stringValue(""),
            "createdAt" to mapOf("timestampValue" to createdAt),
        ),
    )
    seedDocument(
        "trust",
        voterId(),
        mapOf(
            "score" to mapOf("doubleValue" to 0.5),
            "judged" to mapOf("integerValue" to "3"),
            "lastSightingId" to stringValue("old-sighting"),
        ),
    )
  }

  /**
   * Writes a sighting of another user, bypassing the rules, and returns its id: pending and without
   * votes unless told otherwise. Its reporter's trust, if a vote creates it, is deleted with it.
   */
  private suspend fun seedSighting(
      voteWeight: Double? = null,
      status: String = "PENDING",
      trustVerdict: String? = null,
  ): String {
    val sightingId = "sighting-${UUID.randomUUID()}"
    val reporterId = "reporter-${UUID.randomUUID()}"
    val location =
        mapOf(
            "mapValue" to
                mapOf(
                    "fields" to
                        mapOf(
                            "latitude" to mapOf("doubleValue" to 46.5),
                            "longitude" to mapOf("doubleValue" to 6.6),
                        )
                )
        )
    val optional =
        listOfNotNull(
                voteWeight?.let { "voteWeight" to mapOf("doubleValue" to it) },
                trustVerdict?.let { "trustVerdict" to stringValue(it) },
            )
            .toMap()
    seedDocument(
        "sightings",
        sightingId,
        mapOf(
            "id" to stringValue(sightingId),
            "reporterId" to stringValue(reporterId),
            "location" to location,
            "seenAtMillis" to mapOf("integerValue" to "1000000"),
            "species" to stringValue("DOG"),
            "status" to stringValue(status),
        ) + optional,
    )
    seededDocuments += "trust/$reporterId"
    // The vote cast by a test, if any, is deleted with it
    seededDocuments += "sightings/$sightingId/votes/${voterId()}"
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
        // The emulator's admin token: bypasses the rules
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
