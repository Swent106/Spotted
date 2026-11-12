// Portions of this code were generated with the help of Copilot.
package com.android.spotted.model.Pet

import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetRepositoryLocalTest {
  private val repository = PetRepositoryLocal()

  @Test
  fun getNewId_returnsUniqueUUIDs() {
    val firstId = repository.getNewId()
    val secondId = repository.getNewId()

    assertEquals(firstId, UUID.fromString(firstId).toString())
    assertEquals(secondId, UUID.fromString(secondId).toString())
    assertFalse(firstId == secondId)
  }

  @Test
  fun addPet_andGetPet_returnsTheStoredPet() = runBlocking {
    val pet = pet(id = "pet-1")

    assertTrue(repository.addPet(pet).isSuccess)
    assertEquals(pet, repository.getPet(pet.id).getOrThrow())
  }

  @Test
  fun updatePet_replacesAnExistingPet() = runBlocking {
    val original = pet(id = "pet-1")
    val updated = original.copy(name = "Updated", note = "New note")
    repository.addPet(original)

    assertTrue(repository.updatePet(updated).isSuccess)
    assertEquals(updated, repository.getPet(updated.id).getOrThrow())
  }

  @Test
  fun updatePet_whenPetDoesNotExist_returnsFailure() = runBlocking {
    val result = repository.updatePet(pet(id = "missing"))

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is NoSuchElementException)
  }

  @Test
  fun fakePetRepository_supportsUpdateAndDelete() = runBlocking {
    val fake = FakePetRepository()
    val original = pet(id = "pet-1")
    fake.addPet(original)

    val updated = original.copy(name = "Updated")
    assertTrue(fake.updatePet(updated).isSuccess)
    assertEquals(updated, fake.getPet(updated.id).getOrThrow())
    assertTrue(fake.deletePet(updated.id).isSuccess)
    assertTrue(fake.getPet(updated.id).isFailure)
  }

  @Test
  fun deletePet_whenPetDoesNotExist_succeeds() = runBlocking {
    assertTrue(repository.deletePet("missing").isSuccess)
  }

  @Test
  fun deletePet_removesThePet() = runBlocking {
    val pet = pet(id = "pet-1")
    repository.addPet(pet)

    assertTrue(repository.deletePet(pet.id).isSuccess)
    assertTrue(repository.getPet(pet.id).isFailure)
  }

  @Test
  fun getPet_whenPetDoesNotExist_returnsFailure() = runBlocking {
    val result = repository.getPet("missing")

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is NoSuchElementException)
  }

  @Test
  fun getPetsByOwner_returnsOnlyPetsForThatOwner() = runBlocking {
    val ownedPet = pet(id = "pet-1", ownerId = "owner-1")
    repository.addPet(ownedPet)
    repository.addPet(pet(id = "pet-2", ownerId = "owner-2"))

    assertEquals(listOf(ownedPet), repository.getPetsByOwner("owner-1").getOrThrow())
  }

  @Test
  fun getPetsByOwner_whenOwnerHasNoPets_returnsEmptyList() = runBlocking {
    repository.addPet(pet(id = "pet-1", ownerId = "owner-1"))

    assertEquals(emptyList<Pet>(), repository.getPetsByOwner("owner-2").getOrThrow())
  }

  private fun pet(id: String, ownerId: String = "owner-1") =
      Pet(
          id = id,
          ownerId = ownerId,
          name = "Buddy",
          species = Species.DOG,
      )
}
