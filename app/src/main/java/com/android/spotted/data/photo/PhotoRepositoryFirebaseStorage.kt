package com.android.spotted.data.photo

import android.net.Uri
import com.android.spotted.model.photo.PhotoRepository
import com.google.android.gms.tasks.Tasks
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhotoRepositoryFirebaseStorage(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
) : PhotoRepository {

  override suspend fun uploadPetPhoto(
      ownerId: String,
      petId: String,
      localUri: String,
  ): Result<String> = storageResult {
    withContext(Dispatchers.IO) {
      val photoRef = storage.reference.child(petPhotoPath(ownerId, petId))
      val metadata = StorageMetadata.Builder().setContentType(PHOTO_CONTENT_TYPE).build()
      Tasks.await(photoRef.putFile(Uri.parse(localUri), metadata))
      Tasks.await(photoRef.downloadUrl).toString()
    }
  }

  private suspend fun <T> storageResult(action: suspend () -> T): Result<T> =
      try {
        Result.success(action())
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        Result.failure(exception)
      }

  companion object {
    private const val PETS_FOLDER = "pets"
    private const val PHOTO_FILE = "photo.jpg"
    private const val PHOTO_CONTENT_TYPE = "image/jpeg"

    /** Storage path of a pet's photo. The owner is part of the path so rules can check it. */
    fun petPhotoPath(ownerId: String, petId: String): String =
        "$PETS_FOLDER/$ownerId/$petId/$PHOTO_FILE"
  }
}
