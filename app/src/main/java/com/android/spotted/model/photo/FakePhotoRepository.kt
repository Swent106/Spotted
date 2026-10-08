package com.android.spotted.model.photo

import android.net.Uri

class FakePhotoRepository : PhotoRepository {
  var shouldFail = false

  override suspend fun uploadPhoto(uri: Uri, path: String): Result<String> {
    if (shouldFail) {
      return Result.failure(Exception("Failed to upload photo"))
    }
    return Result.success("https://fake-url.com/$path")
  }
}
