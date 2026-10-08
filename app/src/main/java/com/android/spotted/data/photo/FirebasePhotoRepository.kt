package com.android.spotted.data.photo

import android.net.Uri
import com.android.spotted.model.photo.PhotoRepository
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class FirebasePhotoRepository(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) : PhotoRepository {

  override suspend fun uploadPhoto(uri: Uri, path: String): Result<String> {
    return try {
      val storageRef = storage.reference.child(path)
      storageRef.putFile(uri).await()
      val downloadUrl = storageRef.downloadUrl.await()
      Result.success(downloadUrl.toString())
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
