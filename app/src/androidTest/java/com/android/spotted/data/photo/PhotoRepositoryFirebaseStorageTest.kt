package com.android.spotted.data.photo

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoRepositoryFirebaseStorageTest {
  private lateinit var auth: FirebaseAuth
  private lateinit var storage: FirebaseStorage
  private lateinit var repository: PhotoRepositoryFirebaseStorage
  private lateinit var ownerId: String
  private lateinit var localPhoto: File
  private val uploadedPaths = mutableListOf<String>()

  @Before
  fun setUp() = runTest {
    withContext(Dispatchers.IO) {
      auth = FirebaseAuth.getInstance()
      storage = FirebaseStorage.getInstance()
      repository = PhotoRepositoryFirebaseStorage(storage)
      ownerId = Tasks.await(auth.signInAnonymously()).user!!.uid
      localPhoto =
          File.createTempFile(
              "pet",
              ".jpg",
              ApplicationProvider.getApplicationContext<Context>().cacheDir,
          )
      localPhoto.writeBytes(PHOTO_BYTES)
    }
  }

  @After
  fun cleanUp() = runTest {
    withContext(Dispatchers.IO) {
      uploadedPaths.forEach { path -> Tasks.await(storage.reference.child(path).delete()) }
      localPhoto.delete()
      auth.signOut()
    }
  }

  @Test
  fun uploadPetPhoto_asOwner_returnsUrlOfUploadedPhoto() = runTest {
    val result = repository.uploadPetPhoto(ownerId, "pet-1", Uri.fromFile(localPhoto).toString())

    assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
    val path = PhotoRepositoryFirebaseStorage.petPhotoPath(ownerId, "pet-1")
    uploadedPaths += path
    assertFalse(result.getOrThrow().isBlank())
    val storedBytes =
        withContext(Dispatchers.IO) {
          Tasks.await(storage.reference.child(path).getBytes(MAX_DOWNLOAD_BYTES))
        }
    assertArrayEquals(PHOTO_BYTES, storedBytes)
  }

  @Test
  fun uploadPetPhoto_forAnotherOwner_returnsFailure() = runTest {
    val result =
        repository.uploadPetPhoto("someone-else", "pet-1", Uri.fromFile(localPhoto).toString())

    assertTrue(result.isFailure)
  }

  @Test
  fun uploadPetPhoto_withMissingLocalFile_returnsFailure() = runTest {
    val missingFile = File(localPhoto.parentFile, "does-not-exist.jpg")

    val result = repository.uploadPetPhoto(ownerId, "pet-1", Uri.fromFile(missingFile).toString())

    assertTrue(result.isFailure)
  }

  companion object {
    private val PHOTO_BYTES = byteArrayOf(1, 2, 3, 4, 5)
    private const val MAX_DOWNLOAD_BYTES = 1024L

    @JvmStatic
    @BeforeClass
    fun configureFirebaseEmulators() {
      FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
      FirebaseStorage.getInstance().useEmulator("10.0.2.2", 9199)
    }
  }
}
