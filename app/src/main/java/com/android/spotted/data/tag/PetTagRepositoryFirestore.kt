// Portions of this code were generated with the help of Copilot.
package com.android.spotted.data.tag

import com.android.spotted.model.tag.PetTag
import com.android.spotted.model.tag.PetTagRepository
import com.android.spotted.model.tag.TagAlreadyLinkedException
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PetTagRepositoryFirestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val now: () -> Long = System::currentTimeMillis,
) : PetTagRepository {
  private val petTags = firestore.collection(PET_TAGS_COLLECTION)

  override suspend fun linkTag(tagId: String, petId: String): Result<Unit> = firestoreResult {
    withContext(Dispatchers.IO) {
      val ownerId = requireSignedInOwnerId()
      val tagReference = petTags.document(tagId)

      Tasks.await(
          firestore.runTransaction { transaction ->
            val existingTag = transaction.get(tagReference)
            if (existingTag.exists() && existingTag.getString(OWNER_ID_FIELD) != ownerId) {
              throw TagAlreadyLinkedException(tagId)
            }

            transaction.set(
                tagReference,
                mapOf(
                    TAG_ID_FIELD to tagId,
                    PET_ID_FIELD to petId,
                    OWNER_ID_FIELD to ownerId,
                    LINKED_AT_MILLIS_FIELD to now(),
                ),
            )
            Unit
          }
      )
    }
  }

  override suspend fun unlinkTag(tagId: String): Result<Unit> = firestoreResult {
    withContext(Dispatchers.IO) {
      val ownerId = requireSignedInOwnerId()
      val tagReference = petTags.document(tagId)
      val tag = Tasks.await(tagReference.get())
      if (!tag.exists()) return@withContext
      if (tag.getString(OWNER_ID_FIELD) != ownerId) {
        throw SecurityException("Tag $tagId is not owned by the signed-in user")
      }
      Tasks.await(tagReference.delete())
    }
  }

  override suspend fun getTag(tagId: String): Result<PetTag?> = firestoreResult {
    withContext(Dispatchers.IO) {
      requireSignedInOwnerId()
      val snapshot = Tasks.await(petTags.document(tagId).get())
      if (snapshot.exists()) snapshot.toPetTag() else null
    }
  }

  override suspend fun getTagForPet(petId: String): Result<PetTag?> = firestoreResult {
    withContext(Dispatchers.IO) {
      val ownerId = requireSignedInOwnerId()
      val snapshot =
          Tasks.await(
              petTags
                  .whereEqualTo(PET_ID_FIELD, petId)
                  .whereEqualTo(
                      OWNER_ID_FIELD,
                      ownerId,
                  ) // prohibts queries from non owners but they can still use getTag
                  .limit(1)
                  .get()
          )
      snapshot.documents.firstOrNull()?.toPetTag()
    }
  }

  private fun requireSignedInOwnerId(): String =
      firebaseAuth.currentUser?.uid
          ?: throw IllegalStateException("A signed-in user is required for pet tag operations")

  private fun DocumentSnapshot.toPetTag(): PetTag =
      PetTag(
          tagId = requireNotNull(getString(TAG_ID_FIELD)) { "Tag document $id has no tagId" },
          petId = requireNotNull(getString(PET_ID_FIELD)) { "Tag document $id has no petId" },
          ownerId = requireNotNull(getString(OWNER_ID_FIELD)) { "Tag document $id has no ownerId" },
          linkedAtMillis =
              requireNotNull(getLong(LINKED_AT_MILLIS_FIELD)) {
                "Tag document $id has no linkedAtMillis"
              },
      )

  private suspend fun <T> firestoreResult(action: suspend () -> T): Result<T> =
      try {
        Result.success(action())
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: ExecutionException) {
        Result.failure(exception.cause ?: exception)
      } catch (exception: Exception) {
        Result.failure(exception)
      }

  private companion object {
    const val PET_TAGS_COLLECTION = "petTags"
    const val TAG_ID_FIELD = "tagId"
    const val PET_ID_FIELD = "petId"
    const val OWNER_ID_FIELD = "ownerId"
    const val LINKED_AT_MILLIS_FIELD = "linkedAtMillis"
  }
}
