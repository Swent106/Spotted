// Portions of this code were generated with the help of Copilot.
// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.alert

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
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

  /** Seeds a pet and publishes an open alert for it at [location], owned by the signed-in user. */
  private suspend fun publishOpenAlert(location: Location): Alert {
    val ownerId = auth.currentUser!!.uid
    val petId = "pet-${java.util.UUID.randomUUID()}"
    seedPet(petId, ownerId, "Rex")
    val alert =
        Alert(
            id = repository.getNewId(),
            petId = petId,
            ownerId = ownerId,
            lastKnownLocation = location,
            lostAtMillis = 1000L,
            petName = "Rex",
            petSpecies = Species.DOG,
        )
    val result = repository.publishAlert(alert)
    createdAlertIds.add(alert.id)
    assertTrue("Alert should be published: ${result.exceptionOrNull()?.message}", result.isSuccess)
    return alert
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
  fun closeAlert_returnsFailureWhenAlertDoesNotExist() = runBlocking {
    val result = repository.closeAlert("missing-${java.util.UUID.randomUUID()}")

    assertTrue(result.isFailure)
  }
}
