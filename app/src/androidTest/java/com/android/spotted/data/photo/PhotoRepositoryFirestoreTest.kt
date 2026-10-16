package com.android.spotted.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.utils.FirebaseEmulators
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/** Saves photos with [PhotoRepositoryFirestore] in the Firestore emulator and reads them back. */
@RunWith(AndroidJUnit4::class)
class PhotoRepositoryFirestoreTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private lateinit var auth: FirebaseAuth
  private lateinit var firestore: FirebaseFirestore
  private lateinit var repository: PhotoRepositoryFirestore
  private lateinit var ownerId: String
  private val savedPhotoIds = mutableListOf<String>()
  private val localFiles = mutableListOf<File>()

  @Before
  fun setUp() = runTest {
    withContext(Dispatchers.IO) {
      auth = FirebaseAuth.getInstance()
      firestore = FirebaseFirestore.getInstance()
      repository = PhotoRepositoryFirestore(context, firestore)
      ownerId = Tasks.await(auth.signInAnonymously()).user!!.uid
    }
  }

  @After
  fun cleanUp() = runTest {
    withContext(Dispatchers.IO) {
      // A photo that was never saved can't be deleted: the rules need its owner
      savedPhotoIds.forEach { id ->
        runCatching {
          Tasks.await(
              firestore.collection(PhotoRepositoryFirestore.PHOTOS_COLLECTION).document(id).delete()
          )
        }
      }
      localFiles.forEach { it.delete() }
      auth.signOut()
    }
  }

  @Test
  fun uploadPetPhoto_asOwner_savesTheJpegAndReturnsItsReference() = runTest {
    val petId = newPhotoId()

    val result = repository.uploadPetPhoto(ownerId, petId, jpeg(width = 120, height = 80))

    assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
    assertEquals("firestore://photos/$petId", result.getOrThrow())
    val saved = savedDocument(petId)
    assertEquals(ownerId, saved["ownerId"])
    assertEquals("image/jpeg", saved["contentType"])
    val image = decode(saved["data"] as String)
    assertEquals(120, image.width)
    assertEquals(80, image.height)
  }

  @Test
  fun uploadPetPhoto_withABigImage_shrinksItToTheMaximumSide() = runTest {
    val petId = newPhotoId()

    val result = repository.uploadPetPhoto(ownerId, petId, jpeg(width = 2000, height = 1000))

    assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
    val image = decode(savedDocument(petId)["data"] as String)
    assertEquals(640, image.width)
    assertEquals(320, image.height)
  }

  @Test
  fun uploadPetPhoto_withARotationTag_turnsThePhoto() = runTest {
    val petId = newPhotoId()
    val photo = jpeg(width = 300, height = 100, orientation = ExifInterface.ORIENTATION_ROTATE_90)

    val result = repository.uploadPetPhoto(ownerId, petId, photo)

    assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
    val image = decode(savedDocument(petId)["data"] as String)
    assertEquals(100, image.width)
    assertEquals(300, image.height)
  }

  @Test
  fun uploadPetPhoto_withAMissingFile_returnsFailure() = runTest {
    val missing = File(context.cacheDir, "does-not-exist-${UUID.randomUUID()}.jpg")

    val result = repository.uploadPetPhoto(ownerId, newPhotoId(), Uri.fromFile(missing).toString())

    assertTrue(result.isFailure)
  }

  @Test
  fun uploadPetPhoto_withAFileThatIsNotAnImage_returnsFailure() = runTest {
    val text = localFile(".txt").apply { writeText("not an image") }

    val result = repository.uploadPetPhoto(ownerId, newPhotoId(), Uri.fromFile(text).toString())

    assertTrue(result.isFailure)
  }

  @Test
  fun uploadPetPhoto_forAnotherOwner_isRefusedByTheRules() = runTest {
    val result =
        repository.uploadPetPhoto("someone-else", newPhotoId(), jpeg(width = 50, height = 50))

    assertTrue(result.isFailure)
  }

  @Test
  fun uploadProfilePhoto_savesItUnderTheUsersProfilePhotoReference() = runTest {
    savedPhotoIds += "profile_$ownerId"

    val result = repository.uploadProfilePhoto(ownerId, jpeg(width = 64, height = 64))

    assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
    assertEquals(repository.profilePhotoUrl(ownerId), result.getOrThrow())
    assertEquals("firestore://photos/profile_$ownerId", result.getOrThrow())
    assertEquals(ownerId, savedDocument("profile_$ownerId")["ownerId"])
  }

  @Test
  fun photoUrl_pointsToThePhotosCollection() {
    assertEquals("firestore://photos/pet-1", PhotoRepositoryFirestore.photoUrl("pet-1"))
  }

  /** A new photo id, deleted after the test. */
  private fun newPhotoId() = "test-${UUID.randomUUID()}".also { savedPhotoIds += it }

  private fun localFile(suffix: String) =
      File.createTempFile("photo", suffix, context.cacheDir).also { localFiles += it }

  /** Writes a plain JPEG of [width] x [height], with an EXIF [orientation], and returns its URI. */
  private fun jpeg(
      width: Int,
      height: Int,
      orientation: Int = ExifInterface.ORIENTATION_NORMAL,
  ): String {
    val file = localFile(".jpg")
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.rgb(200, 140, 90))
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    if (orientation != ExifInterface.ORIENTATION_NORMAL) {
      ExifInterface(file.absolutePath).apply {
        setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
        saveAttributes()
      }
    }
    return Uri.fromFile(file).toString()
  }

  private suspend fun savedDocument(photoId: String): Map<String, Any> =
      withContext(Dispatchers.IO) {
        Tasks.await(
                firestore
                    .collection(PhotoRepositoryFirestore.PHOTOS_COLLECTION)
                    .document(photoId)
                    .get()
            )
            .data!!
      }

  private fun decode(base64: String): Bitmap {
    val bytes = Base64.decode(base64, Base64.NO_WRAP)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
  }

  companion object {
    @JvmStatic
    @BeforeClass
    fun configureFirebaseEmulators() {
      FirebaseEmulators.configure()
    }
  }
}
