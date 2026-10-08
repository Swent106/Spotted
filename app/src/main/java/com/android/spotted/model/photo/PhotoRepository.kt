package com.android.spotted.model.photo

import android.net.Uri

interface PhotoRepository {
  /** Uploads a photo to a remote storage and returns the download URL. */
  suspend fun uploadPhoto(uri: Uri, path: String): Result<String>
}
