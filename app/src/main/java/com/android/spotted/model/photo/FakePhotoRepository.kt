// Portions of this code were generated with the help of Copilot.
// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.photo

import java.util.concurrent.ConcurrentHashMap

class FakePhotoRepository : PhotoRepository {
  private val photos = ConcurrentHashMap<String, String>()

  var failure: Throwable? = null

  /** Uploaded photos, as a map from the returned URL to the local URI that was uploaded. */
  val uploadedPhotos: Map<String, String>
    get() = photos.toMap()

  override suspend fun uploadPetPhoto(
      ownerId: String,
      petId: String,
      localUri: String,
  ): Result<String> {
    failure?.let {
      return Result.failure(it)
    }
    val url = "$FAKE_URL_PREFIX/pets/$ownerId/$petId/photo.jpg"
    photos[url] = localUri
    return Result.success(url)
  }

  override suspend fun deletePetPhoto(ownerId: String, petId: String): Result<Unit> {
    failure?.let {
      return Result.failure(it)
    }
    photos.remove("$FAKE_URL_PREFIX/pets/$ownerId/$petId/photo.jpg")
    return Result.success(Unit)
  }

  override suspend fun uploadProfilePhoto(userId: String, localUri: String): Result<String> {
    failure?.let {
      return Result.failure(it)
    }
    val url = profilePhotoUrl(userId)
    photos[url] = localUri
    return Result.success(url)
  }

  override suspend fun deleteProfilePhoto(userId: String): Result<Unit> {
    failure?.let {
      return Result.failure(it)
    }
    photos.remove(profilePhotoUrl(userId))
    return Result.success(Unit)
  }

  override fun profilePhotoUrl(userId: String) = "$FAKE_URL_PREFIX/users/$userId/photo.jpg"

  companion object {
    const val FAKE_URL_PREFIX = "fake://storage"
  }
}
