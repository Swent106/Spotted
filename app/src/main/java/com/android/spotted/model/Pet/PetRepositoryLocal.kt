package com.android.spotted.model.Pet

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PetRepositoryLocal : PetRepository {
  private val pets = ConcurrentHashMap<String, Pet>()

  override fun getNewId(): String {
    return UUID.randomUUID().toString()
  }

  override suspend fun addPet(pet: Pet): Result<Unit> {
    pets[pet.id] = pet
    return Result.success(Unit)
  }

  override suspend fun getPet(id: String): Result<Pet> {
    val pet = pets[id]
    return if (pet != null) {
      Result.success(pet)
    } else {
      Result.failure(NoSuchElementException("Pet with id $id was not found"))
    }
  }

  override suspend fun getPetsByOwner(ownerId: String): Result<List<Pet>> {
    return Result.success(pets.values.filter { it.ownerId == ownerId })
  }
}
