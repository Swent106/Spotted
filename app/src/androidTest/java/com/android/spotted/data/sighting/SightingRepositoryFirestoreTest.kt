package com.android.spotted.data.sighting

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.sighting.Sighting
import com.android.spotted.utils.FirebaseEmulators
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SightingRepositoryFirestoreTest {
  private lateinit var auth: FirebaseAuth
  private lateinit var firestore: FirebaseFirestore
  private lateinit var repository: SightingRepositoryFirestore
  private val insertedSightingIds = mutableListOf<String>()

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
        runCatching { Tasks.await(firestore.collection("sightings").document(id).delete()) }
      }
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
    withContext(Dispatchers.IO) {
      Tasks.await(
          firestore
              .collection("sightings")
              .document(invalidId)
              .set(
                  mapOf(
                      "id" to invalidId,
                      "reporterId" to currentUserId(),
                      "location" to mapOf("latitude" to 46.5, "longitude" to 6.6),
                      "seenAtMillis" to 2_000L,
                      "species" to "PARROT",
                      "photoUrl" to null,
                      "alertId" to alertId,
                  )
              )
      )
    }

    assertEquals(listOf(valid), repository.getSightingsForAlert(alertId).getOrThrow())
  }

  private fun currentUserId(): String =
      requireNotNull(auth.currentUser) { "Test user was not authenticated" }.uid

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

  companion object {
    @JvmStatic
    @BeforeClass
    fun configureFirebaseEmulators() {
      FirebaseEmulators.configure()
    }
  }
}
