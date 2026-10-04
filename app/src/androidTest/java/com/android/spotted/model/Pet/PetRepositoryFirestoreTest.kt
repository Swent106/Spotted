package com.android.spotted.model.Pet

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.data.Pet.PetRepositoryFirestore
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
class PetRepositoryFirestoreTest {
  private lateinit var auth: FirebaseAuth
  private lateinit var firestore: FirebaseFirestore
  private lateinit var repository: PetRepositoryFirestore
  private val insertedPetIds = mutableListOf<String>()

  @Before
  fun setUp() = runTest {
    withContext(Dispatchers.IO) {
      auth = FirebaseAuth.getInstance()
      firestore = FirebaseFirestore.getInstance()
      repository = PetRepositoryFirestore(firestore)
      Thread.sleep(3000)
      Tasks.await(auth.signInAnonymously())
    }
  }

  @After
  fun cleanUp() = runTest {
    withContext(Dispatchers.IO) {
      insertedPetIds.forEach { id ->
        Tasks.await(firestore.collection("pets").document(id).delete())
      }
      auth.signOut()
    }
  }

  @Test
  fun getNewId_returnsDistinctDocumentIds() = runTest {
    withContext(Dispatchers.IO) {
      val firstId = repository.getNewId()
      val secondId = repository.getNewId()

      assertFalse(firstId.isBlank())
      assertFalse(secondId.isBlank())
      assertFalse(firstId == secondId)
    }
  }

  @Test
  fun addPet_andGetPet_roundTripsAllPetFields() = runTest {
    withContext(Dispatchers.IO) {
      val pet =
          pet(
              id = repository.getNewId(),
              ownerId = currentUserId(),
              name = "Milo",
              species = Species.CAT,
              breed = "Tabby",
              photoUrl = "https://example.test/milo.jpg",
              allergies = listOf("Pollen", "Dust"),
              behaviors = setOf(Behavior.FEARFUL_OF_STRANGERS, Behavior.STRESSED_IN_CROWDS),
              note = "Needs a quiet place",
          )
      insertedPetIds += pet.id

      assertTrue(repository.addPet(pet).isSuccess)
      assertEquals(pet, repository.getPet(pet.id).getOrThrow())
    }
  }

  @Test
  fun getPet_whenDocumentIsMissing_returnsFailure() = runTest {
    withContext(Dispatchers.IO) {
      val result = repository.getPet(repository.getNewId())

      assertTrue(result.isFailure)
    }
  }

  @Test
  fun getPetsByOwner_returnsOnlyThatOwnersPets() = runTest {
    withContext(Dispatchers.IO) {
      val ownerId = currentUserId()
      val firstPet = pet(id = repository.getNewId(), ownerId = ownerId, name = "First")
      val secondPet = pet(id = repository.getNewId(), ownerId = ownerId, name = "Second")
      insertedPetIds += listOf(firstPet.id, secondPet.id)

      repository.addPet(firstPet).getOrThrow()
      repository.addPet(secondPet).getOrThrow()

      val foundPets = repository.getPetsByOwner(ownerId).getOrThrow()
      assertEquals(setOf(firstPet, secondPet), foundPets.toSet())
    }
  }

  @Test
  fun getPetsByOwner_whenOwnerHasNoPets_returnsEmptyList() = runTest {
    withContext(Dispatchers.IO) {
      assertTrue(repository.getPetsByOwner(currentUserId()).getOrThrow().isEmpty())
    }
  }

  @Test
  fun getPetsByOwner_whenRequestingAnotherOwner_returnsFailure() = runTest {
    withContext(Dispatchers.IO) {
      val result = repository.getPetsByOwner("another-owner")

      assertTrue(result.isFailure)
    }
  }

  private fun currentUserId(): String =
      requireNotNull(auth.currentUser) { "Test user was not authenticated" }.uid

  private fun pet(
      id: String,
      ownerId: String,
      name: String = "Buddy",
      species: Species = Species.DOG,
      breed: String = "",
      photoUrl: String? = null,
      allergies: List<String> = emptyList(),
      behaviors: Set<Behavior> = emptySet(),
      note: String = "",
  ) =
      Pet(
          id = id,
          ownerId = ownerId,
          name = name,
          species = species,
          breed = breed,
          photoUrl = photoUrl,
          allergies = allergies,
          behaviors = behaviors,
          note = note,
      )

  companion object {
    @JvmStatic
    @BeforeClass
    fun configureFirebaseEmulators() {
      FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
      FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)
    }
  }
}
