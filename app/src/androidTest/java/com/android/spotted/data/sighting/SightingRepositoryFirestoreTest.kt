// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.sighting

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.sighting.Sighting
import com.android.spotted.model.sighting.SightingStatus
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SightingRepositoryFirestoreTest {
  private lateinit var auth: FirebaseAuth
  private lateinit var firestore: FirebaseFirestore
  private lateinit var repository: SightingRepositoryFirestore
  private val insertedSightingIds = mutableListOf<String>()
  private val insertedAlertIds = mutableListOf<String>()

  @Before
  fun setUp() = runTest {
    withContext(Dispatchers.IO) {
      auth = FirebaseAuth.getInstance()
      firestore = FirebaseFirestore.getInstance()
      repository = SightingRepositoryFirestore(firestore)
      Tasks.await(auth.signInAnonymously())
    }
  }

  @After
  fun cleanUp() = runTest {
    withContext(Dispatchers.IO) {
      insertedSightingIds.forEach { id ->
        Tasks.await(firestore.collection("sightings").document(id).delete())
      }
      // Some alerts belong to another user, so they are deleted as the admin
      insertedAlertIds.forEach { id -> emulatorRequest(method = "DELETE", path = "alerts/$id") }
      auth.signOut()
    }
  }

  @Test
  fun getNewId_returnsDistinctDocumentIds() {
    val firstId = repository.getNewId()
    val secondId = repository.getNewId()

    assertFalse(firstId.isBlank())
    assertFalse(secondId.isBlank())
    assertFalse(firstId == secondId)
  }

  @Test
  fun reportSighting_storesAllFieldsInFirestore() = runTest {
    val alertId = repository.getNewId()
    val sighting =
        sighting(
            id = repository.getNewId(),
            alertId = alertId,
            photoUrl = "https://example.test/milo.jpg",
        )

    assertTrue(repository.reportSighting(sighting).isSuccess)

    assertEquals(listOf(sighting), repository.getSightingsForAlert(alertId).getOrThrow())
  }

  @Test
  fun reportSighting_withoutAlertOrPhoto_isStored() = runTest {
    val sighting = sighting(id = repository.getNewId(), alertId = null, photoUrl = null)

    assertTrue(repository.reportSighting(sighting).isSuccess)

    val snapshot =
        withContext(Dispatchers.IO) {
          Tasks.await(firestore.collection("sightings").document(sighting.id).get())
        }
    assertTrue(snapshot.exists())
    assertEquals(currentUserId(), snapshot.getString("reporterId"))
  }

  @Test
  fun reportSighting_sameIdAgain_replacesSighting() = runTest {
    val alertId = repository.getNewId()
    val first = sighting(id = repository.getNewId(), alertId = alertId, species = Species.DOG)
    val replacement = first.copy(species = Species.CAT, seenAtMillis = 5_000L)

    repository.reportSighting(first).getOrThrow()
    repository.reportSighting(replacement).getOrThrow()

    assertEquals(listOf(replacement), repository.getSightingsForAlert(alertId).getOrThrow())
  }

  @Test
  fun reportSighting_forAnotherReporter_returnsFailure() = runTest {
    val sighting = sighting(id = repository.getNewId(), reporterId = "someone-else")
    // The write is denied, so there is nothing to clean up (deleting it would be denied too)
    insertedSightingIds -= sighting.id

    assertTrue(repository.reportSighting(sighting).isFailure)
  }

  @Test
  fun getSightingsForAlert_returnsOnlyThatAlertsSightings_mostRecentFirst() = runTest {
    val alertId = repository.getNewId()
    val otherAlertId = repository.getNewId()
    val older =
        sighting(id = "b-${repository.getNewId()}", alertId = alertId, seenAtMillis = 1_000L)
    val newer = sighting(id = repository.getNewId(), alertId = alertId, seenAtMillis = 3_000L)
    val sameTimeAsOlder =
        sighting(id = "a-${repository.getNewId()}", alertId = alertId, seenAtMillis = 1_000L)
    val otherAlert = sighting(id = repository.getNewId(), alertId = otherAlertId)
    val noAlert = sighting(id = repository.getNewId(), alertId = null)

    listOf(older, newer, sameTimeAsOlder, otherAlert, noAlert).forEach {
      repository.reportSighting(it).getOrThrow()
    }

    assertEquals(
        listOf(newer, sameTimeAsOlder, older),
        repository.getSightingsForAlert(alertId).getOrThrow(),
    )
  }

  @Test
  fun getSightingsForAlert_whenNone_returnsEmptyList() = runTest {
    assertTrue(repository.getSightingsForAlert(repository.getNewId()).getOrThrow().isEmpty())
  }

  @Test
  fun getSightingsForAlert_skipsSightingsWithUnknownSpecies() = runTest {
    val alertId = repository.getNewId()
    val valid = sighting(id = repository.getNewId(), alertId = alertId)
    repository.reportSighting(valid).getOrThrow()
    val invalidId = repository.getNewId()
    insertedSightingIds += invalidId
    // The rules reject unknown species, so the old or broken document is written as an admin
    seedSightingBypassingRules(invalidId, alertId, species = "PARROT")

    assertEquals(listOf(valid), repository.getSightingsForAlert(alertId).getOrThrow())
  }

  @Test
  fun getSightingsForAlert_readsStatus_andTreatsMissingStatusAsPending() = runTest {
    val alertId = repository.getNewId()
    val legacyId = repository.getNewId()
    val unknownStatusId = repository.getNewId()
    insertedSightingIds += listOf(legacyId, unknownStatusId)
    // Sightings saved before the status existed have none; unknown values are skipped
    seedSightingBypassingRules(legacyId, alertId, species = "DOG", status = null)
    seedSightingBypassingRules(unknownStatusId, alertId, species = "DOG", status = "MAYBE")

    assertEquals(
        listOf(legacyId to SightingStatus.PENDING),
        repository.getSightingsForAlert(alertId).getOrThrow().map { it.id to it.status },
    )
  }

  @Test
  fun observeSightingsForAlert_emitsCurrentSightingsThenEveryChange() = runTest {
    val alertId = seedAlertBypassingRules(ownerId = currentUserId())
    val first =
        sighting(id = "b-${repository.getNewId()}", alertId = alertId, seenAtMillis = 1_000L)
    val sameTime =
        sighting(id = "a-${repository.getNewId()}", alertId = alertId, seenAtMillis = 1_000L)
    val otherAlert = sighting(id = repository.getNewId(), alertId = repository.getNewId())
    repository.reportSighting(first).getOrThrow()

    inRealTime {
      val updates = repository.observeSightingsForAlert(alertId).produceIn(this)

      updates.awaitSightings(listOf(first))
      repository.reportSighting(otherAlert).getOrThrow()
      repository.reportSighting(sameTime).getOrThrow()
      updates.awaitSightings(listOf(sameTime, first))
      repository.setSightingStatus(first.id, SightingStatus.CONFIRMED).getOrThrow()
      updates.awaitSightings(listOf(sameTime, first.copy(status = SightingStatus.CONFIRMED)))

      updates.cancel()
    }
  }

  @Test
  fun observeSightingsForAlert_whenNone_emitsEmptyList() = runTest {
    val alertId = repository.getNewId()

    inRealTime {
      val firstEmission = repository.observeSightingsForAlert(alertId).first()
      assertEquals(emptyList<Sighting>(), firstEmission.getOrThrow())
    }
  }

  @Test
  fun observeSightingsForAlert_stopsListeningWhenCollectorIsCancelled() = runTest {
    val alertId = repository.getNewId()
    val before = sighting(id = repository.getNewId(), alertId = alertId)
    val after = sighting(id = repository.getNewId(), alertId = alertId, seenAtMillis = 2_000L)
    repository.reportSighting(before).getOrThrow()

    val received = CopyOnWriteArrayList<List<Sighting>>()
    inRealTime {
      val listening = CompletableDeferred<Unit>()
      val collector = launch {
        repository.observeSightingsForAlert(alertId).collect {
          received += it.getOrThrow()
          if (it.getOrThrow() == listOf(before)) listening.complete(Unit)
        }
      }
      listening.await()
      collector.cancelAndJoin()
      val receivedBeforeCancel = received.toList()

      repository.reportSighting(after).getOrThrow()
      // A new listener sees the change, so the cancelled one would have received it by now too
      repository.observeSightingsForAlert(alertId).first { it.getOrNull() == listOf(after, before) }

      assertEquals(receivedBeforeCancel, received.toList())
    }
  }

  @Test
  fun setSightingStatus_byAlertOwner_changesOnlyTheStatus() = runTest {
    val alertId = seedAlertBypassingRules(ownerId = currentUserId())
    val sighting = sighting(id = repository.getNewId(), alertId = alertId)
    repository.reportSighting(sighting).getOrThrow()

    assertTrue(repository.setSightingStatus(sighting.id, SightingStatus.DISMISSED).isSuccess)

    assertEquals(
        listOf(sighting.copy(status = SightingStatus.DISMISSED)),
        repository.getSightingsForAlert(alertId).getOrThrow(),
    )
  }

  @Test
  fun setSightingStatus_returnsFailureWhenNotAllowed() = runTest {
    val someoneElsesAlert = seedAlertBypassingRules(ownerId = "someone-else")
    val notMyAlert = sighting(id = repository.getNewId(), alertId = someoneElsesAlert)
    val noAlert = sighting(id = repository.getNewId(), alertId = null)
    repository.reportSighting(notMyAlert).getOrThrow()
    repository.reportSighting(noAlert).getOrThrow()

    assertTrue(repository.setSightingStatus(notMyAlert.id, SightingStatus.CONFIRMED).isFailure)
    assertTrue(repository.setSightingStatus(noAlert.id, SightingStatus.CONFIRMED).isFailure)
    assertTrue(
        repository.setSightingStatus(repository.getNewId(), SightingStatus.CONFIRMED).isFailure
    )
    assertEquals(
        SightingStatus.PENDING,
        repository.getSightingsForAlert(someoneElsesAlert).getOrThrow().single().status,
    )
  }

  private fun currentUserId(): String =
      requireNotNull(auth.currentUser) { "Test user was not authenticated" }.uid

  /**
   * Runs [block] on a real dispatcher: `runTest` skips delays, so a timeout would fire before
   * Firestore answers.
   */
  private suspend fun inRealTime(block: suspend CoroutineScope.() -> Unit) =
      withContext(Dispatchers.Default) { withTimeout(TIMEOUT_MILLIS) { block() } }

  /** Skips emissions until the sightings are [expected] (local writes can emit several times). */
  private suspend fun ReceiveChannel<Result<List<Sighting>>>.awaitSightings(
      expected: List<Sighting>
  ) {
    while (receive().getOrThrow() != expected) {
      // Wait for the next emission
    }
  }

  /** Writes a minimal alert (only what the sighting rules read) and returns its id. */
  private suspend fun seedAlertBypassingRules(ownerId: String): String {
    val id = repository.getNewId()
    insertedAlertIds += id
    seedBypassingRules(
        collection = "alerts",
        id = id,
        fields =
            JSONObject()
                .put("id", JSONObject().put("stringValue", id))
                .put("ownerId", JSONObject().put("stringValue", ownerId))
                .put("status", JSONObject().put("stringValue", "OPEN")),
    )
    return id
  }

  private suspend fun seedSightingBypassingRules(
      id: String,
      alertId: String,
      species: String,
      status: String? = null,
  ) {
    val fields =
        JSONObject()
            .put("id", JSONObject().put("stringValue", id))
            .put("reporterId", JSONObject().put("stringValue", currentUserId()))
            .put(
                "location",
                JSONObject()
                    .put(
                        "mapValue",
                        JSONObject()
                            .put(
                                "fields",
                                JSONObject()
                                    .put("latitude", JSONObject().put("doubleValue", 46.5))
                                    .put("longitude", JSONObject().put("doubleValue", 6.6)),
                            ),
                    ),
            )
            .put("seenAtMillis", JSONObject().put("integerValue", "2000"))
            .put("species", JSONObject().put("stringValue", species))
            .put("photoUrl", JSONObject().put("nullValue", JSONObject.NULL))
            .put("alertId", JSONObject().put("stringValue", alertId))
    status?.let { fields.put("status", JSONObject().put("stringValue", it)) }
    seedBypassingRules(collection = "sightings", id = id, fields = fields)
  }

  /**
   * Writes a document through the Firestore emulator's REST API with its "owner" token, which
   * bypasses the security rules.
   */
  private suspend fun seedBypassingRules(collection: String, id: String, fields: JSONObject) {
    val response =
        emulatorRequest(
            method = "POST",
            path = "$collection?documentId=$id",
            body = JSONObject().put("fields", fields),
        )
    assertEquals(HttpURLConnection.HTTP_OK, response)
  }

  /** Sends [method] to the emulator's REST API for [path] as the admin; returns the HTTP code. */
  private suspend fun emulatorRequest(method: String, path: String, body: JSONObject? = null): Int {
    val projectId = firestore.app.options.projectId
    val url =
        URL(
            "http://${firestore.firestoreSettings.host}/v1/projects/$projectId" +
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
          connection.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        connection.responseCode
      } finally {
        connection.disconnect()
      }
    }
  }

  private companion object {
    const val TIMEOUT_MILLIS = 10_000L
  }

  private fun sighting(
      id: String,
      reporterId: String = currentUserId(),
      alertId: String? = null,
      seenAtMillis: Long = 1_000L,
      species: Species = Species.DOG,
      photoUrl: String? = null,
  ): Sighting {
    insertedSightingIds += id
    return Sighting(
        id = id,
        reporterId = reporterId,
        location = Location(latitude = 46.5191, longitude = 6.5668),
        seenAtMillis = seenAtMillis,
        species = species,
        photoUrl = photoUrl,
        alertId = alertId,
    )
  }
}
