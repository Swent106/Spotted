package com.android.spotted.data.photo

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.ImageLoader
import coil.fetch.SourceResult
import coil.request.Options
import com.android.spotted.utils.FirebaseEmulators
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/** Loads `firestore://photos/{id}` references with [FirestorePhotoFetcher] from the emulator. */
@RunWith(AndroidJUnit4::class)
class FirestorePhotoFetcherTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val options = Options(context)
  private val imageLoader = ImageLoader(context)
  private lateinit var auth: FirebaseAuth
  private lateinit var firestore: FirebaseFirestore
  private lateinit var factory: FirestorePhotoFetcher.Factory
  private lateinit var ownerId: String
  private val savedPhotoIds = mutableListOf<String>()

  @Before
  fun setUp() = runTest {
    withContext(Dispatchers.IO) {
      auth = FirebaseAuth.getInstance()
      firestore = FirebaseFirestore.getInstance()
      factory = FirestorePhotoFetcher.Factory { firestore }
      ownerId = Tasks.await(auth.signInAnonymously()).user!!.uid
    }
  }

  @After
  fun cleanUp() = runTest {
    withContext(Dispatchers.IO) {
      savedPhotoIds.forEach { id ->
        Tasks.await(
            firestore.collection(PhotoRepositoryFirestore.PHOTOS_COLLECTION).document(id).delete()
        )
      }
      auth.signOut()
    }
  }

  @Test
  fun factory_onlyHandlesFirestorePhotoReferences() {
    assertNotNull(factory.create(Uri.parse("firestore://photos/pet-1"), options, imageLoader))
    assertNull(factory.create(Uri.parse("https://example.com/pet.jpg"), options, imageLoader))
    assertNull(factory.create(Uri.parse("firestore://pets/pet-1"), options, imageLoader))
    assertNull(factory.create(Uri.parse("firestore://photos"), options, imageLoader))
  }

  @Test
  fun fetch_returnsTheSavedImageBytes() = runTest {
    val photoId = savePhoto(PHOTO_BYTES)
    val fetcher =
        factory.create(
            Uri.parse(PhotoRepositoryFirestore.photoUrl(photoId)),
            options,
            imageLoader,
        )!!

    val result = fetcher.fetch() as SourceResult

    assertEquals("image/jpeg", result.mimeType)
    assertArrayEquals(PHOTO_BYTES, result.source.source().readByteArray())
  }

  @Test
  fun fetch_withAMissingPhoto_fails() = runTest {
    val uri = Uri.parse(PhotoRepositoryFirestore.photoUrl("missing-${UUID.randomUUID()}"))
    val fetcher = factory.create(uri, options, imageLoader)!!

    val failure = runCatching { fetcher.fetch() }.exceptionOrNull()

    assertTrue(failure.toString(), failure is NoSuchElementException)
  }

  /** Saves [bytes] as a photo owned by the signed-in user and returns its id. */
  private suspend fun savePhoto(bytes: ByteArray): String =
      withContext(Dispatchers.IO) {
        val photoId = "test-${UUID.randomUUID()}"
        Tasks.await(
            firestore
                .collection(PhotoRepositoryFirestore.PHOTOS_COLLECTION)
                .document(photoId)
                .set(
                    mapOf(
                        "ownerId" to ownerId,
                        "contentType" to "image/jpeg",
                        "data" to Base64.encodeToString(bytes, Base64.NO_WRAP),
                    )
                )
        )
        savedPhotoIds += photoId
        photoId
      }

  companion object {
    private val PHOTO_BYTES =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())

    @JvmStatic
    @BeforeClass
    fun configureFirebaseEmulators() {
      FirebaseEmulators.configure()
    }
  }
}
