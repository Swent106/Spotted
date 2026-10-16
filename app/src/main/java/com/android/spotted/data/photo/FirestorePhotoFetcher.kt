package com.android.spotted.data.photo

import android.net.Uri
import android.util.Base64
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import okio.Buffer

/**
 * Lets Coil load the `firestore://photos/{id}` references made by [PhotoRepositoryFirestore]: it
 * reads the base64 photo from Firestore and hands Coil the image bytes. Other URLs are left to
 * Coil's own fetchers.
 */
class FirestorePhotoFetcher(
    private val photoId: String,
    private val options: Options,
    private val firestore: FirebaseFirestore,
) : Fetcher {

  override suspend fun fetch(): FetchResult {
    val snapshot =
        firestore
            .collection(PhotoRepositoryFirestore.PHOTOS_COLLECTION)
            .document(photoId)
            .get()
            .await()
    val data = snapshot.getString("data") ?: throw NoSuchElementException("No photo $photoId")
    val bytes = Base64.decode(data, Base64.NO_WRAP)
    return SourceResult(
        source = ImageSource(Buffer().write(bytes), options.context),
        mimeType = "image/jpeg",
        dataSource = DataSource.NETWORK,
    )
  }

  /** Creates a [FirestorePhotoFetcher] for `firestore://photos/{id}` URLs only. */
  class Factory(private val firestore: () -> FirebaseFirestore = FirebaseFirestore::getInstance) :
      Fetcher.Factory<Uri> {
    override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
      if (data.scheme != PhotoRepositoryFirestore.SCHEME) return null
      if (data.host != PhotoRepositoryFirestore.PHOTOS_COLLECTION) return null
      val photoId = data.lastPathSegment ?: return null
      return FirestorePhotoFetcher(photoId, options, firestore())
    }
  }
}
