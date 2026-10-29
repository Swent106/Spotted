package com.android.spotted.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import com.android.spotted.model.photo.PhotoRepository
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [PhotoRepository] that keeps photos in Firestore instead of Firebase Storage.
 *
 * The photo is shrunk to at most [MAX_SIDE_PX] pixels and compressed as JPEG until it is under
 * [MAX_BYTES], then saved in base64 in `photos/{petId}` (a Firestore document holds at most 1 MiB).
 * Profile photos go to `photos/profile_{uid}`, so the profile document itself is unchanged. The
 * returned URL is a short `firestore://photos/{petId}` reference, so it fits in pets, alerts and
 * sightings; [FirestorePhotoFetcher] loads it for Coil.
 */
class PhotoRepositoryFirestore(
    private val context: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : PhotoRepository {

  override suspend fun uploadPetPhoto(
      ownerId: String,
      petId: String,
      localUri: String,
  ): Result<String> = upload(photoId = petId, ownerId = ownerId, localUri = localUri)

  override suspend fun uploadProfilePhoto(userId: String, localUri: String): Result<String> =
      upload(photoId = profilePhotoId(userId), ownerId = userId, localUri = localUri)

  override fun profilePhotoUrl(userId: String) = photoUrl(profilePhotoId(userId))

  /** Saves the image at [localUri] as `photos/{photoId}`, owned by [ownerId]. */
  private suspend fun upload(photoId: String, ownerId: String, localUri: String): Result<String> =
      try {
        withContext(Dispatchers.IO) {
          val jpeg = compress(Uri.parse(localUri))
          val document =
              mapOf(
                  "ownerId" to ownerId,
                  "contentType" to CONTENT_TYPE,
                  "data" to Base64.encodeToString(jpeg, Base64.NO_WRAP),
              )
          Tasks.await(firestore.collection(PHOTOS_COLLECTION).document(photoId).set(document))
          Result.success(photoUrl(photoId))
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        Result.failure(exception)
      }

  /** Decodes the image at [uri], shrinks it and compresses it until it fits in [MAX_BYTES]. */
  private fun compress(uri: Uri): ByteArray {
    val resolver = context.applicationContext.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
    // Decode at a power-of-two fraction first, so a big camera photo doesn't fill the memory
    var sampleSize = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_SIDE_PX) {
      sampleSize *= 2
    }
    val decoded =
        resolver.openInputStream(uri).use {
          BitmapFactory.decodeStream(
              it,
              null,
              BitmapFactory.Options().apply { inSampleSize = sampleSize },
          )
        } ?: throw IllegalArgumentException("$uri is not an image")
    // Cameras save the photo sideways with an EXIF tag saying how to turn it: apply it, or the
    // photo is shown rotated (and cropped wrong in the round and square frames)
    val orientation =
        resolver.openInputStream(uri)?.use {
          ExifInterface(it)
              .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    val scale = minOf(1f, MAX_SIDE_PX.toFloat() / max(decoded.width, decoded.height))
    val matrix = orientationMatrix(orientation).apply { postScale(scale, scale) }
    val bitmap =
        if (matrix.isIdentity) decoded
        else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

    var quality = START_QUALITY
    while (true) {
      val bytes =
          ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
          }
      if (bytes.size <= MAX_BYTES || quality <= MIN_QUALITY) return bytes
      quality -= QUALITY_STEP
    }
  }

  /** Turns and flips the photo as its EXIF [orientation] says. */
  private fun orientationMatrix(orientation: Int) =
      Matrix().apply {
        when (orientation) {
          ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
          ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
          ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
          ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
          ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
          ExifInterface.ORIENTATION_TRANSPOSE -> {
            postRotate(90f)
            postScale(-1f, 1f)
          }
          ExifInterface.ORIENTATION_TRANSVERSE -> {
            postRotate(270f)
            postScale(-1f, 1f)
          }
        }
      }

  companion object {
    const val PHOTOS_COLLECTION = "photos"
    const val SCHEME = "firestore"
    private const val CONTENT_TYPE = "image/jpeg"
    private const val MAX_SIDE_PX = 640
    /** Leaves room for base64 (4/3 larger) under Firestore's 1 MiB document limit. */
    private const val MAX_BYTES = 500_000
    private const val START_QUALITY = 80
    private const val MIN_QUALITY = 35
    private const val QUALITY_STEP = 15

    /** Id of a user's profile photo: one per user, replaced when they pick another. */
    private fun profilePhotoId(userId: String) = "profile_$userId"

    /** Reference stored in pets, alerts and sightings for the photo saved as [photoId]. */
    fun photoUrl(photoId: String) = "$SCHEME://$PHOTOS_COLLECTION/$photoId"
  }
}
