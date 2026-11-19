// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.sighting

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.fakes.FakePendingSightingRepository
import com.android.spotted.model.fakes.FakeSightingRepository
import com.android.spotted.model.photo.FakePhotoRepository
import com.android.spotted.model.sighting.PendingSighting
import com.android.spotted.model.sighting.PendingSightingSender
import com.android.spotted.model.sighting.Sighting
import com.android.spotted.model.sighting.SightingStatus
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class PendingSightingSendWorkerTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val pending = FakePendingSightingRepository()
  private val sightings = FakeSightingRepository()

  @Before
  fun setUp() {
    WorkManagerTestInitHelper.initializeTestWorkManager(
        context,
        Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build(),
    )
  }

  @Test
  fun doWork_whenEverythingIsSent_succeeds() = runTest {
    pending.add(sighting("s1"))

    val result = buildWorker().doWork()

    assertEquals(ListenableWorker.Result.success(), result)
    assertEquals(listOf("s1"), sightings.reportedSightings.map { it.id })
  }

  @Test
  fun doWork_whenOffline_asksToRetry() = runTest {
    pending.add(sighting("s1"))
    sightings.reportFailure = IOException("offline")

    assertEquals(ListenableWorker.Result.retry(), buildWorker().doWork())
    assertEquals(1, pending.pending.size)
  }

  @Test
  fun doWork_whenASightingIsRefused_logsTheReason_andSucceeds() = runTest {
    // Already reviewed by the owner: the server refuses to replace it
    sightings.reportSighting(sighting("s1").copy(status = SightingStatus.CONFIRMED))
    pending.add(sighting("s1"))

    val result = buildWorker().doWork()

    assertEquals(ListenableWorker.Result.success(), result)
    assertEquals(emptyList<PendingSighting>(), pending.pending)
    assertTrue(
        ShadowLog.getLogsForTag("PendingSightingSend").any {
          it.msg == "Dropped sighting s1: Sighting s1 cannot be changed"
        }
    )
  }

  @Test
  fun doWork_whenSendingThrowsUnexpectedly_logsIt_andAsksToRetry() = runTest {
    pending.add(sighting("s1"))

    val result = buildWorker { throw IllegalStateException("Firebase not ready") }.doWork()

    assertEquals(ListenableWorker.Result.retry(), result)
    assertEquals(1, pending.pending.size)
    assertTrue(
        ShadowLog.getLogsForTag("PendingSightingSend").any {
          it.type == Log.ERROR && it.throwable?.message == "Firebase not ready"
        }
    )
  }

  @Test
  fun doWork_whenCancelled_stopsInsteadOfRetrying() = runTest {
    val worker = buildWorker { throw CancellationException("stopped by WorkManager") }

    assertThrows(CancellationException::class.java) { runBlocking { worker.doWork() } }
  }

  @Test
  fun schedule_waitsForTheNetwork_andQueuesASecondSendAfterTheFirst() {
    PendingSightingSendWorker.schedule(context)
    PendingSightingSendWorker.schedule(context)

    val works = uniqueWorks()
    assertEquals(2, works.size)
    assertTrue(works.all { it.constraints.requiredNetworkType == NetworkType.CONNECTED })
    // Not run yet (no network in the test), and the second one waits for the first
    assertEquals(
        setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED),
        works.map { it.state }.toSet(),
    )
  }

  @Test
  fun theAppRepository_isShared_copiesThePhoto_andSchedulesASend() = runTest {
    val picked = File(context.cacheDir, "picked.jpg").apply { writeBytes(byteArrayOf(7, 8)) }
    val repository = PendingSightingRepositoryProvider.get(context)

    try {
      assertTrue(repository === PendingSightingRepositoryProvider.get(context))
      repository.add(sighting("s1"), photoUri = Uri.fromFile(picked).toString()).getOrThrow()

      val copy = repository.list().getOrThrow().single().photoUri!!
      assertTrue(File(Uri.parse(copy).path!!).readBytes().contentEquals(byteArrayOf(7, 8)))
      assertEquals(listOf(WorkInfo.State.ENQUEUED), uniqueWorks().map { it.state })
    } finally {
      repository.delete("s1")
    }
  }

  private fun uniqueWorks(): List<WorkInfo> =
      WorkManager.getInstance(context)
          .getWorkInfosForUniqueWork(PendingSightingSendWorker.WORK_NAME)
          .get()

  private fun buildWorker(
      createSender: (Context) -> PendingSightingSender = {
        PendingSightingSender(
            pending,
            sightings,
            FakePhotoRepository(),
            currentUserId = { "reporter" },
            isRefusal = { it is IllegalStateException },
        )
      }
  ): PendingSightingSendWorker =
      TestListenableWorkerBuilder<PendingSightingSendWorker>(context)
          .setWorkerFactory(
              object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ) = PendingSightingSendWorker(appContext, workerParameters, createSender)
              }
          )
          .build()

  private fun sighting(id: String) =
      Sighting(
          id = id,
          reporterId = "reporter",
          location = Location(46.5, 6.6),
          seenAtMillis = 1_000L,
          species = Species.DOG,
          alertId = "alert-1",
      )
}
