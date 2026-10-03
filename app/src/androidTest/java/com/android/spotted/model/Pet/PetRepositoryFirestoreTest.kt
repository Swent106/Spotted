package com.android.spotted.model.Pet

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.data.Pet.PetRepositoryFirestore
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID
import kotlinx.coroutines.runBlocking
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
  private lateinit var firestore: FirebaseFirestore
  private lateinit var repository: PetRepositoryFirestore
  private val insertedPetIds = mutableListOf<String>()

  @Before
  fun setUp() {
    firestore = FirebaseFirestore.getInstance()
    repository = PetRepositoryFirestore(firestore)
  }

  @After
  fun cleanUp() {
    insertedPetIds.forEach { id -> Tasks.await(firestore.collection("pets").document(id).delete()) }
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
  fun addPet_andGetPet_roundTripsAllPetFields() = runBlocking {
    val pet =
        pet(
            id = repository.getNewId(),
            ownerId = UUID.randomUUID().toString(),
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

  @Test
  fun getPet_whenDocumentIsMissing_returnsFailure() = runBlocking {
    val result = repository.getPet(repository.getNewId())

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is NoSuchElementException)
  }

  @Test
  fun getPetsByOwner_returnsOnlyThatOwnersPets() = runBlocking {
    val ownerId = UUID.randomUUID().toString()
    val firstPet = pet(id = repository.getNewId(), ownerId = ownerId, name = "First")
    val secondPet = pet(id = repository.getNewId(), ownerId = ownerId, name = "Second")
    val otherOwnersPet =
        pet(
            id = repository.getNewId(),
            ownerId = UUID.randomUUID().toString(),
            name = "Other",
        )
    insertedPetIds += listOf(firstPet.id, secondPet.id, otherOwnersPet.id)

    repository.addPet(firstPet).getOrThrow()
    repository.addPet(secondPet).getOrThrow()
    repository.addPet(otherOwnersPet).getOrThrow()

    val foundPets = repository.getPetsByOwner(ownerId).getOrThrow()
    assertEquals(setOf(firstPet, secondPet), foundPets.toSet())
  }

  @Test
  fun getPetsByOwner_whenOwnerHasNoPets_returnsEmptyList() = runBlocking {
    assertTrue(repository.getPetsByOwner(UUID.randomUUID().toString()).getOrThrow().isEmpty())
  }

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
    fun configureFirestoreEmulator() {
      FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)
    }
  }
}
