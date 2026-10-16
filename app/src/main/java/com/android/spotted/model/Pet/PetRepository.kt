package com.android.spotted.model.Pet

interface PetRepository {
  fun getNewId(): String

  suspend fun addPet(pet: Pet): Result<Unit>

  suspend fun getPet(id: String): Result<Pet>

  suspend fun getPetsByOwner(ownerId: String): Result<List<Pet>>

  /** Deletes the pet [id]. Deleting a pet that doesn't exist succeeds. */
  suspend fun deletePet(id: String): Result<Unit>
}
