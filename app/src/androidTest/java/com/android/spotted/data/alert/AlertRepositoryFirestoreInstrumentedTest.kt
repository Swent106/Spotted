// Portions of this code were generated with the help of Copilot.
// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.alert

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertAlreadyOpenException
import com.android.spotted.model.alert.AlertStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlertRepositoryFirestoreInstrumentedTest {

  private lateinit var firestore: FirebaseFirestore
  private lateinit var auth: FirebaseAuth
  private lateinit var repository: AlertRepositoryFirestore
  private var nowMillis = 0L

  // Documents created during a test, deleted in tearDown
  private val createdPetIds = mutableListOf<String>()
  private val createdAlertIds = mutableListOf<String>()

  @Before
  fun setUp() = runBlocking {
    firestore = FirebaseFirestore.getInstance()
    auth = FirebaseAuth.getInstance()
    repository = AlertRepositoryFirestore(firestore, now = { nowMillis })

    // Anonymous sign-in so that Firestore rules see an authenticated user
    auth.signInAnonymously().await()
    assertNotNull("User should be signed in", auth.currentUser)
  }

  @After
  fun tearDown() = runBlocking {
    // Best-effort cleanup: ignore failures (e.g. rules forbidding deletes)
    createdAlertIds.forEach { id ->
      runCatching { firestore.collection("alerts").document(id).delete().await() }
    }
    createdPetIds.forEach { id ->
      runCatching { firestore.collection("openPetAlerts").document(id).delete().await() }
      runCatching { firestore.collection("pets").document(id).delete().await() }
    }
    createdAlertIds.clear()
    createdPetIds.clear()
    auth.signOut()
  }

  /** Seeds a pet owned by [ownerId] so that the Alert security rules pass. */
  private suspend fun seedPet(
      petId: String,
      ownerId: String,
      name: String = "Pet",
      species: Species = Species.DOG,
  ) {
    firestore
        .collection("pets")
        .document(petId)
        .set(
            mapOf(
                "id" to petId,
                "ownerId" to ownerId,
                "name" to name,
                "species" to species.name,
                "breed" to "",
                "photoUrl" to null,
                "allergies" to emptyList<String>(),
                "behaviors" to emptyList<String>(), // Set -> List, Firestore can't store Sets
                "note" to "",
            )
        )
        .await()
    createdPetIds.add(petId)
  }

  @Test
  fun getNewId_returnsUniqueNonEmptyString() {
    val id1 = repository.getNewId()
    val id2 = repository.getNewId()

    assertTrue(id1.isNotEmpty())
    assertTrue(id2.isNotEmpty())
    assertNotEquals(id1, id2)
  }

  @Test
  fun publishAlert_storesAlertInEmulator() = runBlocking {
    val ownerId = auth.currentUser!!.uid
    val petId = "pet-${java.util.UUID.randomUUID()}"
    seedPet(petId, ownerId, "Rex")

    val alertId = repository.getNewId()
    val alert =
        Alert(
            id = alertId,
            petId = petId,
            ownerId = ownerId,
            lastKnownLocation = Location(latitude = 46.5196, longitude = 6.6322),
            lostAtMillis = 1000000L,
            petName = "Rex",
            petSpecies = Species.DOG,
        )

    val result = repository.publishAlert(alert)
    createdAlertIds.add(alertId)
    assertTrue(
        "publishAlert should be successful: ${result.exceptionOrNull()?.message}",
        result.isSuccess,
    )

    // Verify it was actually written to the emulator
    val snapshot = firestore.collection("alerts").document(alertId).get().await()
    assertTrue("Document should exist in emulator", snapshot.exists())
    assertEquals("Rex", snapshot.getString("petName"))
    assertEquals(alertId, snapshot.getString("id"))
    assertEquals(ownerId, snapshot.getString("ownerId"))
  }

  @Test
  fun getAlertById_returnsAlertWithCopiedPetDetails() = runBlocking {
    val ownerId = auth.currentUser!!.uid
    val petId = "pet-${java.util.UUID.randomUUID()}"
    seedPet(petId, ownerId, "Luna", Species.CAT)

    val alert =
        Alert(
            id = repository.getNewId(),
            petId = petId,
            ownerId = ownerId,
            lastKnownLocation = Location(latitude = 46.5196, longitude = 6.6322),
            lostAtMillis = 1_700_000_000_000L,
            status = AlertStatus.OPEN,
            petName = "Luna",
            petSpecies = Species.CAT,
            petPhotoUrl = "https://example.com/luna.jpg",
            petAllergies = listOf("chicken"),
            petBehaviors = setOf(Behavior.FEARFUL_OF_STRANGERS),
        )
    val publishResult = repository.publishAlert(alert)
    createdAlertIds.add(alert.id)
    assertTrue(
        "Alert should be published: ${publishResult.exceptionOrNull()?.message}",
        publishResult.isSuccess,
    )

    val result = repository.getAlertById(alert.id)

    assertTrue("Lookup should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
    assertEquals(alert, result.getOrNull())
  }

  @Test
  fun getAlertById_returnsNullWhenDocumentDoesNotExist() = runBlocking {
    val missingAlertId = "missing-${java.util.UUID.randomUUID()}"

    val result = repository.getAlertById(missingAlertId)

    assertTrue("Lookup should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
    assertEquals(null, result.getOrNull())
  }

  @Test
  fun getOpenAlertsNear_fetchesOnlyOpenAlertsWithinRadius() = runBlocking {
    val ownerId = auth.currentUser!!.uid
    val petId1 = "pet-${java.util.UUID.randomUUID()}"
    val petId2 = "pet-${java.util.UUID.randomUUID()}"
    seedPet(petId1, ownerId, "Close", Species.DOG)
    seedPet(petId2, ownerId, "Far", Species.CAT)

    val center = Location(latitude = 46.0, longitude = 6.0)

    // 1. Open and nearby
    val alert1 =
        Alert(
            id = repository.getNewId(),
            petId = petId1,
            ownerId = ownerId,
            lastKnownLocation = Location(latitude = 46.001, longitude = 6.001),
            lostAtMillis = 2000L,
            petName = "Close",
            petSpecies = Species.DOG,
        )
    // 2. Open but far away
    val alert2 =
        Alert(
            id = repository.getNewId(),
            petId = petId2,
            ownerId = ownerId,
            lastKnownLocation = Location(latitude = 47.0, longitude = 7.0),
            lostAtMillis = 3000L,
            petName = "Far",
            petSpecies = Species.CAT,
        )

    val publish1 = repository.publishAlert(alert1)
    createdAlertIds.add(alert1.id)
    val publish2 = repository.publishAlert(alert2)
    createdAlertIds.add(alert2.id)

    assertTrue(
        "alert1 should be published: ${publish1.exceptionOrNull()?.message}",
        publish1.isSuccess,
    )
    assertTrue(
        "alert2 should be published: ${publish2.exceptionOrNull()?.message}",
        publish2.isSuccess,
    )

    // Act: search within a 5 km radius
    val result = repository.getOpenAlertsNear(center, radiusKm = 5.0)

    // Assert
    assertTrue("Query should be successful: ${result.exceptionOrNull()?.message}", result.isSuccess)
    val alerts = result.getOrNull()
    assertNotNull("Alerts list should not be null", alerts)

    assertTrue("Nearby alert should be found", alerts!!.any { it.id == alert1.id })
    assertTrue("Far alert should NOT be found", alerts.none { it.id == alert2.id })
  }

  @Test
  fun getCachedOpenAlertsNear_returnsAlertsAfterNetworkIsDisabled() = runBlocking {
    val center = Location(latitude = 45.0, longitude = 5.0)
    val alerts =
        (1..6).map { index ->
          publishOpenAlert(
              Location(latitude = center.latitude + index * 0.0001, longitude = center.longitude),
              lostAtMillis = index * 1000L,
          )
        }
    val online = repository.getOpenAlertsNear(center, radiusKm = 1.0).getOrThrow()
    assertEquals(alerts.map { it.id }.toSet(), online.map { it.id }.toSet())

    firestore.disableNetwork().await()
    try {
      val onlineQuery = repository.getOpenAlertsNear(center, radiusKm = 1.0)
      assertTrue(
          "Server query should fail without a network connection",
          onlineQuery.isFailure,
      )

      val cached = repository.getCachedOpenAlertsNear(center, radiusKm = 1.0)

      assertTrue(
          "Cache query should succeed: ${cached.exceptionOrNull()?.message}",
          cached.isSuccess,
      )
      assertEquals(alerts.takeLast(5).reversed().map { it.id }, cached.getOrThrow().map { it.id })
    } finally {
      firestore.enableNetwork().await()
    }
  }

  @Test
  fun observeOpenAlertsNear_emitsAlertsPublishedAfterCollectionStarts() = runBlocking {
    val center = Location(latitude = 44.0, longitude = 4.0)
    val before = publishOpenAlert(center)

    withTimeout(TIMEOUT_MILLIS) {
      val updates = repository.observeOpenAlertsNear(center, radiusKm = 1.0).produceIn(this)
      updates.awaitAlertIds { before.id in it }

      val far = publishOpenAlert(Location(latitude = 44.5, longitude = 4.5))
      val after =
          publishOpenAlert(Location(latitude = 44.0001, longitude = 4.0), lostAtMillis = 2000L)
      val ids = updates.awaitAlertIds { after.id in it }

      assertTrue("Far alert should NOT be emitted", far.id !in ids)
      assertEquals(listOf(after.id, before.id), ids.filter { it == after.id || it == before.id })
      updates.cancel()
    }
  }

  @Test
  fun observeOpenAlertsNear_stopsListeningWhenCollectorIsCancelled() = runBlocking {
    val center = Location(latitude = 43.0, longitude = 3.0)
    val before = publishOpenAlert(center)
    val received = CopyOnWriteArrayList<List<String>>()

    withTimeout(TIMEOUT_MILLIS) {
      val listening = CompletableDeferred<Unit>()
      val collector = launch {
        repository.observeOpenAlertsNear(center, radiusKm = 1.0).collect { result ->
          val ids = result.getOrThrow().map { it.id }
          received += ids
          if (before.id in ids) listening.complete(Unit)
        }
      }
      listening.await()
      collector.cancelAndJoin()
      val receivedBeforeCancel = received.toList()

      val after = publishOpenAlert(center, lostAtMillis = 2000L)
      // A new listener sees the change, so the cancelled one would have received it by now too
      repository.observeOpenAlertsNear(center, radiusKm = 1.0).first { result ->
        result.getOrThrow().any { it.id == after.id }
      }

      assertEquals(receivedBeforeCancel, received.toList())
    }
  }

  @Test
  fun observeOpenAlertsNear_whenSignedOut_emitsFailureAndEnds() = runBlocking {
    // The rules only let signed-in users list alerts, so the listener is refused
    auth.signOut()

    // Far from the other tests' alerts. Firestore may still emit its local cache first.
    val emissions =
        withTimeout(TIMEOUT_MILLIS) {
          repository
              .observeOpenAlertsNear(Location(latitude = 10.0, longitude = 10.0), radiusKm = 1.0)
              .toList()
        }

    assertTrue("The flow should end with a failure", emissions.last().isFailure)
    assertTrue("Only the last emission is a failure", emissions.dropLast(1).all { it.isSuccess })
  }

  /**
   * Skips emissions until [done] holds for their alert ids (local writes can emit several times).
   */
  private suspend fun ReceiveChannel<Result<List<Alert>>>.awaitAlertIds(
      done: (List<String>) -> Boolean
  ): List<String> {
    while (true) {
      val ids = receive().getOrThrow().map { it.id }
      if (done(ids)) return ids
    }
  }

  /** Seeds a pet and publishes an open alert for it at [location], owned by the signed-in user. */
  private suspend fun publishOpenAlert(location: Location, lostAtMillis: Long = 1000L): Alert {
    val ownerId = auth.currentUser!!.uid
    val petId = "pet-${java.util.UUID.randomUUID()}"
    seedPet(petId, ownerId, "Rex")
    val alert =
        Alert(
            id = repository.getNewId(),
            petId = petId,
            ownerId = ownerId,
            lastKnownLocation = location,
            lostAtMillis = lostAtMillis,
            petName = "Rex",
            petSpecies = Species.DOG,
        )
    val result = repository.publishAlert(alert)
    createdAlertIds.add(alert.id)
    assertTrue("Alert should be published: ${result.exceptionOrNull()?.message}", result.isSuccess)
    return alert
  }

  /** A new open alert of the signed-in user for [petId], not published. */
  private fun alertFor(petId: String) =
      Alert(
          id = repository.getNewId(),
          petId = petId,
          ownerId = auth.currentUser!!.uid,
          lastKnownLocation = Location(latitude = 46.0, longitude = 6.0),
          lostAtMillis = 1000L,
          petName = "Rex",
          petSpecies = Species.DOG,
      )

  @Test
  fun publishAlert_twoAtOnceForTheSamePet_publishesOnlyOne() = runBlocking {
    val petId = "pet-${java.util.UUID.randomUUID()}"
    seedPet(petId, auth.currentUser!!.uid, "Rex")
    val first = alertFor(petId)
    val second = alertFor(petId)
    createdAlertIds += listOf(first.id, second.id)

    val results =
        listOf(first, second)
            .map { alert -> async(Dispatchers.IO) { repository.publishAlert(alert) } }
            .awaitAll()

    assertEquals("Exactly one publish should succeed: $results", 1, results.count { it.isSuccess })
    val refusal = results.single { it.isFailure }.exceptionOrNull()
    assertTrue(
        "Expected AlertAlreadyOpenException, got $refusal",
        refusal is AlertAlreadyOpenException,
    )
    val stored = firestore.collection("alerts").whereEqualTo("petId", petId).limit(10).get().await()
    assertEquals(1, stored.size())
  }

  @Test
  fun publishAlert_petWithAnOpenAlertFromBeforeTheLocks_isRefused() = runBlocking {
    val petId = "pet-${java.util.UUID.randomUUID()}"
    seedPet(petId, auth.currentUser!!.uid, "Rex")
    val old = alertFor(petId)
    createdAlertIds += old.id
    repository.publishAlert(old).getOrThrow()
    // Alerts opened before this change have no lock. The rules no longer let a client delete the
    // lock of an open alert, so it is deleted as the emulator's admin.
    deleteBypassingRules("openPetAlerts/$petId")
    val second = alertFor(petId)
    createdAlertIds += second.id

    val result = repository.publishAlert(second)

    assertEquals(old.id, (result.exceptionOrNull() as? AlertAlreadyOpenException)?.openAlertId)
    assertEquals(null, repository.getAlertById(second.id).getOrThrow())
  }

  /**
   * Deletes the document at [path] through the emulator's REST API as its admin, skipping rules.
   */
  private suspend fun deleteBypassingRules(path: String) {
    val url =
        URL(
            "http://${firestore.firestoreSettings.host}/v1/projects/" +
                "${firestore.app.options.projectId}/databases/(default)/documents/$path"
        )
    val code =
        withContext(Dispatchers.IO) {
          val connection = url.openConnection() as HttpURLConnection
          try {
            connection.requestMethod = "DELETE"
            connection.setRequestProperty("Authorization", "Bearer owner")
            connection.responseCode
          } finally {
            connection.disconnect()
          }
        }
    assertEquals(HttpURLConnection.HTTP_OK, code)
  }

  @Test
  fun publishAlert_afterTheOpenAlertIsClosed_succeeds() = runBlocking {
    val petId = "pet-${java.util.UUID.randomUUID()}"
    seedPet(petId, auth.currentUser!!.uid, "Rex")
    val first = alertFor(petId)
    createdAlertIds += first.id
    repository.publishAlert(first).getOrThrow()
    val second = alertFor(petId)
    createdAlertIds += second.id
    val refused = repository.publishAlert(second)
    assertEquals(first.id, (refused.exceptionOrNull() as? AlertAlreadyOpenException)?.openAlertId)

    // The rules refuse a closing time before lostAtMillis (1000)
    nowMillis = 5_000L
    repository.closeAlert(first.id).getOrThrow()
    val result = repository.publishAlert(second)

    assertTrue(
        "Publish after closing should succeed: ${result.exceptionOrNull()}",
        result.isSuccess,
    )
    assertEquals(AlertStatus.OPEN, repository.getAlertById(second.id).getOrThrow()?.status)
  }

  @Test
  fun closeAlert_removesAlertFromOpenAlertsNear_butKeepsItInMyAlerts() = runBlocking {
    val center = Location(latitude = 46.0, longitude = 6.0)
    val alert = publishOpenAlert(center)
    nowMillis = 5_000L

    val result = repository.closeAlert(alert.id)

    assertTrue("closeAlert should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
    val openAlerts = repository.getOpenAlertsNear(center, radiusKm = 5.0).getOrThrow()
    assertTrue("Closed alert should NOT be found", openAlerts.none { it.id == alert.id })
    val myAlert = repository.getMyAlerts(alert.ownerId).getOrThrow().single { it.id == alert.id }
    assertEquals(alert.copy(status = AlertStatus.CLOSED, closedAtMillis = 5_000L), myAlert)
  }

  @Test
  fun closeAlert_twice_keepsFirstClosingTime() = runBlocking {
    val alert = publishOpenAlert(Location(latitude = 46.0, longitude = 6.0))
    nowMillis = 5_000L
    repository.closeAlert(alert.id).getOrThrow()
    nowMillis = 9_000L

    val result = repository.closeAlert(alert.id)

    assertTrue(
        "Second close should succeed: ${result.exceptionOrNull()?.message}",
        result.isSuccess,
    )
    assertEquals(5_000L, repository.getAlertById(alert.id).getOrThrow()?.closedAtMillis)
  }

  @Test
  fun closeAlert_returnsNoSuchElementWhenAlertDoesNotExist() = runBlocking {
    val result = repository.closeAlert("missing-${java.util.UUID.randomUUID()}")

    assertTrue(
        "Expected NoSuchElementException, got ${result.exceptionOrNull()}",
        result.exceptionOrNull() is NoSuchElementException,
    )
  }

  @Test
  fun getMyAlerts_isSortedByFirestore_newestFirst() = runBlocking {
    val location = Location(latitude = 46.0, longitude = 6.0)
    // Published oldest last, so the order can't come from the order of writes
    val newest = publishOpenAlert(location, lostAtMillis = 3_000L)
    val oldest = publishOpenAlert(location, lostAtMillis = 1_000L)
    val middle = publishOpenAlert(location, lostAtMillis = 2_000L)

    val result = repository.getMyAlerts(auth.currentUser!!.uid)

    assertTrue("Query should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
    assertEquals(listOf(newest.id, middle.id, oldest.id), result.getOrThrow().map { it.id })
  }

  private companion object {
    const val TIMEOUT_MILLIS = 10_000L
  }
}
