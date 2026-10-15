package com.android.spotted.model.photo

interface PhotoRepository {
  /**
   * Uploads the image at [localUri] as the photo of pet [petId], owned by [ownerId], and returns
   * the URL where the uploaded photo can be downloaded.
   */
  suspend fun uploadPetPhoto(ownerId: String, petId: String, localUri: String): Result<String>

  /** Uploads the image at [localUri] as the profile photo of user [userId] and returns its URL. */
  suspend fun uploadProfilePhoto(userId: String, localUri: String): Result<String>

  /** URL of the profile photo of [userId]; nothing is shown there until one is uploaded. */
  fun profilePhotoUrl(userId: String): String
}
