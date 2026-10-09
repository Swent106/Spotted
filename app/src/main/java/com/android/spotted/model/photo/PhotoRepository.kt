package com.android.spotted.model.photo

interface PhotoRepository {
  /**
   * Uploads the image at [localUri] as the photo of pet [petId], owned by [ownerId], and returns
   * the URL where the uploaded photo can be downloaded.
   */
  suspend fun uploadPetPhoto(ownerId: String, petId: String, localUri: String): Result<String>
}
