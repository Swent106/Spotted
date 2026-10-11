package com.android.spotted.data.alert

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
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
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlertRepositoryFirestoreInstrumentedTest {

  private lateinit var firestore: FirebaseFirestore
  private lateinit var auth: FirebaseAuth
  private lateinit var repository: AlertRepositoryFirestore

  // Documents created during a test, deleted in tearDown
  private val createdPetIds = mutableListOf<String>()
  private val createdAlertIds = mutableListOf<String>()

  @Before
  fun setUp() = runBlocking {
    firestore = FirebaseFirestore.getInstance()
    auth = FirebaseAuth.getInstance()
    repository = AlertRepositoryFirestore(firestore)

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
      species: Species = Species.DOG
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
                "note" to ""))
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
            petSpecies = Species.DOG)

    val result = repository.publishAlert(alert)
    createdAlertIds.add(alertId)
    assertTrue(
        "publishAlert should be successful: ${result.exceptionOrNull()?.message}", result.isSuccess)

    // Verify it was actually written to the emulator
    val snapshot = firestore.collection("alerts").document(alertId).get().await()
    assertTrue("Document should exist in emulator", snapshot.exists())
    assertEquals("Rex", snapshot.getString("petName"))
    assertEquals(alertId, snapshot.getString("id"))
    assertEquals(ownerId, snapshot.getString("ownerId"))
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
            petSpecies = Species.DOG)
    // 2. Open but far away
    val alert2 =
        Alert(
            id = repository.getNewId(),
            petId = petId2,
            ownerId = ownerId,
            lastKnownLocation = Location(latitude = 47.0, longitude = 7.0),
            lostAtMillis = 3000L,
            petName = "Far",
            petSpecies = Species.CAT)

    val publish1 = repository.publishAlert(alert1)
    createdAlertIds.add(alert1.id)
    val publish2 = repository.publishAlert(alert2)
    createdAlertIds.add(alert2.id)

    assertTrue(
        "alert1 should be published: ${publish1.exceptionOrNull()?.message}", publish1.isSuccess)
    assertTrue(
        "alert2 should be published: ${publish2.exceptionOrNull()?.message}", publish2.isSuccess)

    // Act: search within a 5 km radius
    val result = repository.getOpenAlertsNear(center, radiusKm = 5.0)

    // Assert
    assertTrue("Query should be successful: ${result.exceptionOrNull()?.message}", result.isSuccess)
    val alerts = result.getOrNull()
    assertNotNull("Alerts list should not be null", alerts)

    assertTrue("Nearby alert should be found", alerts!!.any { it.id == alert1.id })
    assertTrue("Far alert should NOT be found", alerts.none { it.id == alert2.id })
  }

  companion object {
    @JvmStatic
    @BeforeClass
    fun configureFirebaseEmulators() {
      // useEmulator() must be called before any other use of Firestore/Auth in the process.
      try {
        FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)
        FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
      } catch (e: IllegalStateException) {
        // Don't swallow silently: if this fires, tests may be hitting the real backend.
        println("Emulator config failed (already initialized?): ${e.message}")
      }
    }
  }
}
