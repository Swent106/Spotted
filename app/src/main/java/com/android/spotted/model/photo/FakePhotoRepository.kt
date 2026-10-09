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

  companion object {
    const val FAKE_URL_PREFIX = "fake://storage"
  }
}
