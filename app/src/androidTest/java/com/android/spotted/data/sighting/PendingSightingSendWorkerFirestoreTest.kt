// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.sighting

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.android.spotted.data.photo.PhotoRepositoryFirestore
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.auth.AuthRepositoryProvider
import com.android.spotted.model.sighting.PendingSightingRepository
import com.android.spotted.model.sighting.Sighting
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Queues sightings in the app-wide [PendingSightingRepositoryProvider] and sends them with the real
 * [PendingSightingSendWorker], built by WorkManager as in the app, through the Firebase emulators.
 */
@RunWith(AndroidJUnit4::class)
class PendingSightingSendWorkerFirestoreTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val auth = FirebaseAuth.getInstance()
  private val firestore = FirebaseFirestore.getInstance()
  private val ids = SightingRepositoryFirestore(firestore)
  private lateinit var queue: PendingSightingRepository
  private lateinit var userId: String
  private val sentSightingIds = mutableListOf<String>()
  private val localFiles = mutableListOf<File>()

  @Before
  fun setUp() = runBlocking {
    // Scheduled sends only run when a test says so, so they can't run in the middle of a test
    WorkManagerTestInitHelper.initializeTestWorkManager(
        context,
        Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
    )
    queue = PendingSightingRepositoryProvider.get(context)
    clearQueue()
    userId = withContext(Dispatchers.IO) { Tasks.await(auth.signInAnonymously()).user!!.uid }
    awaitSignedInUser(userId)
  }

  @After
  fun cleanUp() = runBlocking {
    clearQueue()
    withContext(Dispatchers.IO) {
      // The test user can't delete a sighting, so they are deleted as the admin
      sentSightingIds.forEach { id ->
        emulatorRequest("DELETE", "sightings/$id")
        emulatorRequest("DELETE", "${PhotoRepositoryFirestore.PHOTOS_COLLECTION}/$id")
      }
      localFiles.forEach { it.delete() }
    }
    auth.signOut()
    awaitSignedInUser(null)
  }

  @Test
  fun aQueuedSighting_isSentByTheScheduledWork_onceTheNetworkIsBack() = runBlocking {
    val sighting = sighting(seenAtMillis = 1_700_000_000_000L)

    queue.add(sighting).getOrThrow()

    // Waits for the network: nothing is sent yet
    val work = scheduledWork()
    assertEquals(WorkInfo.State.ENQUEUED, work.state)
    assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
    assertFalse(sentDocument(sighting.id).exists())

    WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(work.id)

    assertEquals(WorkInfo.State.SUCCEEDED, awaitFinished(work.id))
    val sent = sentDocument(sighting.id)
    assertEquals(userId, sent.getString("reporterId"))
    // The time it was seen, not the time it was sent
    assertEquals(1_700_000_000_000L, sent.getLong("seenAtMillis"))
    assertEquals(0, queue.list().getOrThrow().size)
  }

  @Test
  fun runningTheWorkerAgain_sendsTheSightingOnlyOnce() = runBlocking {
    val sighting = sighting()
    queue.add(sighting).getOrThrow()

    assertEquals(ListenableWorker.Result.success(), runWorker())
    val firstSend = sentDocument(sighting.id)
    assertEquals(ListenableWorker.Result.success(), runWorker())

    // The first run emptied the queue, so the second one sent nothing new
    assertEquals(0, queue.list().getOrThrow().size)
    assertEquals(firstSend.data, sentDocument(sighting.id).data)
  }

  @Test
  fun aQueuedSightingWithAPhoto_isSentWithTheUploadedPhoto() = runBlocking {
    val sighting = sighting()
    queue.add(sighting, photoUri = jpeg()).getOrThrow()

    assertEquals(ListenableWorker.Result.success(), runWorker())

    assertEquals(
        "firestore://photos/${sighting.id}",
        sentDocument(sighting.id).getString("photoUrl"),
    )
    val photo =
        withContext(Dispatchers.IO) {
          Tasks.await(
              firestore
                  .collection(PhotoRepositoryFirestore.PHOTOS_COLLECTION)
                  .document(sighting.id)
                  .get()
          )
        }
    assertEquals(userId, photo.getString("ownerId"))
    assertEquals(0, queue.list().getOrThrow().size)
  }

  @Test
  fun whenSignedOut_nothingIsSent_andTheSightingWaitsInTheQueue() = runBlocking {
    val sighting = sighting()
    queue.add(sighting).getOrThrow()
    auth.signOut()
    awaitSignedInUser(null)

    assertEquals(ListenableWorker.Result.retry(), runWorker())

    assertEquals(
        HttpURLConnection.HTTP_NOT_FOUND,
        emulatorRequest("GET", "sightings/${sighting.id}"),
    )
    assertEquals(listOf(sighting.id), queue.list().getOrThrow().map { it.sighting.id })
  }

  /** Builds the worker the way WorkManager does in the app (no test factory) and runs it. */
  private suspend fun runWorker(): ListenableWorker.Result =
      TestListenableWorkerBuilder<PendingSightingSendWorker>(context).build().doWork()

  private fun scheduledWork(): WorkInfo =
      WorkManager.getInstance(context)
          .getWorkInfosForUniqueWork(PendingSightingSendWorker.WORK_NAME)
          .get()
          .single()

  private suspend fun awaitFinished(id: UUID): WorkInfo.State =
      withTimeout(TIMEOUT_MILLIS) {
        var state = WorkManager.getInstance(context).getWorkInfoById(id).get()!!.state
        while (!state.isFinished) {
          delay(POLL_MILLIS)
          state = WorkManager.getInstance(context).getWorkInfoById(id).get()!!.state
        }
        state
      }

  /** The worker reads the user from the app's auth repository, which follows Firebase later. */
  private suspend fun awaitSignedInUser(uid: String?) {
    withTimeout(TIMEOUT_MILLIS) {
      AuthRepositoryProvider.authRepository.currentUser.first { it?.uid == uid }
    }
  }

  private suspend fun clearQueue() {
    queue.list().getOrThrow().forEach { queue.delete(it.sighting.id).getOrThrow() }
  }

  private suspend fun sentDocument(id: String): DocumentSnapshot =
      withContext(Dispatchers.IO) {
        Tasks.await(firestore.collection("sightings").document(id).get())
      }

  private fun sighting(seenAtMillis: Long = 1_000L): Sighting {
    val id = ids.getNewId()
    sentSightingIds += id
    return Sighting(
        id = id,
        reporterId = userId,
        location = Location(latitude = 46.5191, longitude = 6.5668),
        seenAtMillis = seenAtMillis,
        species = Species.CAT,
    )
  }

  /** A small JPEG on the phone, as the Photo Picker would give. */
  private suspend fun jpeg(): String =
      withContext(Dispatchers.IO) {
        val file = File.createTempFile("pending-sighting", ".jpg", context.cacheDir)
        localFiles += file
        val bitmap = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        Uri.fromFile(file).toString()
      }

  /** Calls the Firestore emulator's REST API as the admin, which bypasses the rules. */
  private suspend fun emulatorRequest(method: String, path: String): Int =
      withContext(Dispatchers.IO) {
        val projectId = firestore.app.options.projectId
        val url =
            URL(
                "http://${firestore.firestoreSettings.host}/v1/projects/$projectId" +
                    "/databases/(default)/documents/$path"
            )
        val connection = url.openConnection() as HttpURLConnection
        try {
          connection.requestMethod = method
          connection.setRequestProperty("Authorization", "Bearer owner")
          connection.responseCode
        } finally {
          connection.disconnect()
        }
      }

  private companion object {
    const val TIMEOUT_MILLIS = 20_000L
    const val POLL_MILLIS = 100L
  }
}
