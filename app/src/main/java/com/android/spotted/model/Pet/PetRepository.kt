package com.android.spotted.model.Pet

interface PetRepository {
  fun getNewId(): String

  suspend fun addPet(pet: Pet): Result<Unit>

  suspend fun getPet(id: String): Result<Pet>

  suspend fun getPetsByOwner(ownerId: String): Result<List<Pet>>
}
