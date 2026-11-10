// Portions of this code were generated with the help of Claude (Anthropic).
// Portions of this code were generated with the help of Copilot.
package com.android.spotted.data.Pet

import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepository
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.photo.PhotoRepository
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pets stored in Firestore. [photoRepository] has no default: it must be the backend the app saves
 * pet photos with, or [deletePet] would look for the photo in the wrong place and leave it behind.
 */
class PetRepositoryFirestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val photoRepository: PhotoRepository,
) : PetRepository {
  private val pets = firestore.collection(PETS_COLLECTION)

  override fun getNewId(): String {
    return pets.document().id
  }

  override suspend fun addPet(pet: Pet): Result<Unit> = firestoreResult {
    withContext(Dispatchers.IO) {
      Tasks.await(pets.document(pet.id).set(PetDocument.from(pet)))
      Unit
    }
  }

  override suspend fun updatePet(pet: Pet): Result<Unit> = firestoreResult {
    withContext(Dispatchers.IO) {
      Tasks.await(pets.document(pet.id).update(PetDocument.from(pet).toUpdateMap()))
      Unit
    }
  }

  override suspend fun getPet(id: String): Result<Pet> = firestoreResult {
    withContext(Dispatchers.IO) {
      val snapshot = Tasks.await(pets.document(id).get())
      if (snapshot.exists()) {
        val petDocument =
            snapshot.toObject(PetDocument::class.java)
                ?: throw IllegalStateException("Pet document $id could not be decoded")
        petDocument.toPet(snapshot.id)
      } else {
        throw NoSuchElementException("Pet with id $id was not found")
      }
    }
  }

  override suspend fun getPetsByOwner(ownerId: String): Result<List<Pet>> = firestoreResult {
    withContext(Dispatchers.IO) {
      val snapshots = Tasks.await(pets.whereEqualTo("ownerId", ownerId).get())
      snapshots.documents.map { snapshot ->
        val petDocument =
            snapshot.toObject(PetDocument::class.java)
                ?: throw IllegalStateException("Pet document ${snapshot.id} could not be decoded")
        petDocument.toPet(snapshot.id)
      }
    }
  }

  override suspend fun deletePet(id: String): Result<Unit> = firestoreResult {
    withContext(Dispatchers.IO) {
      val petReference = pets.document(id)
      val snapshot = Tasks.await(petReference.get())
      if (snapshot.exists()) {
        val petDocument =
            snapshot.toObject(PetDocument::class.java)
                ?: throw IllegalStateException("Pet document $id could not be decoded")
        photoRepository.deletePetPhoto(petDocument.ownerId, id).getOrThrow()
        Tasks.await(petReference.delete())
      }
      Unit
    }
  }

  private suspend fun <T> firestoreResult(action: suspend () -> T): Result<T> =
      try {
        Result.success(action())
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        Result.failure(exception)
      }

  private class PetDocument {
    var ownerId: String = ""
    var name: String = ""
    var species: String = Species.OTHER.name
    var breed: String = ""
    var photoUrl: String? = null
    var allergies: List<String> = emptyList()
    var behaviors: List<String> = emptyList()
    var note: String = ""

    // The rules don't check species and behaviors, so a value this version doesn't know (e.g.
    // added by a newer app) is read as OTHER or skipped: it must not hide all of the owner's pets
    fun toPet(id: String): Pet =
        Pet(
            id = id,
            ownerId = ownerId,
            name = name,
            species = Species.entries.firstOrNull { it.name == species } ?: Species.OTHER,
            breed = breed,
            photoUrl = photoUrl,
            allergies = allergies,
            behaviors =
                behaviors.mapNotNullTo(mutableSetOf()) { name ->
                  Behavior.entries.firstOrNull { it.name == name }
                },
            note = note,
        )

    fun toUpdateMap(): Map<String, Any> =
        mapOf(
            "ownerId" to ownerId,
            "name" to name,
            "species" to species,
            "breed" to breed,
            "photoUrl" to (photoUrl ?: FieldValue.delete()),
            "allergies" to allergies,
            "behaviors" to behaviors,
            "note" to note,
        )

    companion object {
      fun from(pet: Pet): PetDocument =
          PetDocument().apply {
            ownerId = pet.ownerId
            name = pet.name
            species = pet.species.name
            breed = pet.breed
            photoUrl = pet.photoUrl
            allergies = pet.allergies
            behaviors = pet.behaviors.map(Behavior::name)
            note = pet.note
          }
    }
  }

  private companion object {
    const val PETS_COLLECTION = "pets"
  }
}
