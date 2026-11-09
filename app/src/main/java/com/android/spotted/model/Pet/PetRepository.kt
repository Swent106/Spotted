// Portions of this code were generated with the help of Copilot.
package com.android.spotted.model.Pet

interface PetRepository {
  fun getNewId(): String

  suspend fun addPet(pet: Pet): Result<Unit>

  suspend fun updatePet(pet: Pet): Result<Unit>

  suspend fun getPet(id: String): Result<Pet>

  suspend fun getPetsByOwner(ownerId: String): Result<List<Pet>>

  /** Deletes the pet [id] and its photo. */
  suspend fun deletePet(id: String): Result<Unit>
}
