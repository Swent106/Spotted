// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.sighting

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.android.spotted.data.photo.PhotoRepositoryFirestore
import com.android.spotted.model.auth.AuthRepositoryProvider
import com.android.spotted.model.sighting.PendingSightingSender
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Sends the queued sightings with [PendingSightingSender] once the phone is online.
 *
 * WorkManager runs it only with a network connection and keeps it scheduled when the app is closed
 * or the phone restarts. When some sightings could not be sent (offline, nobody signed in), it is
 * retried later with exponential backoff.
 *
 * If the network is lost while it runs, WorkManager cancels it, but a write already waiting in
 * `Tasks.await` keeps its thread until Firestore answers, so it may finish during the next run.
 * That is harmless: the sighting keeps its id, so sending it twice writes the same document.
 *
 * @param createSender Builds the sender; tests pass one using fakes.
 */
class PendingSightingSendWorker(
    context: Context,
    params: WorkerParameters,
    private val createSender: (Context) -> PendingSightingSender,
) : CoroutineWorker(context, params) {

  /** Constructor WorkManager uses: sends through the Firestore repositories. */
  @Suppress("unused")
  constructor(
      context: Context,
      params: WorkerParameters,
  ) : this(context, params, ::productionSender)

  override suspend fun doWork(): Result {
    val report =
        try {
          createSender(applicationContext).sendAll()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          // Unexpected (e.g. Firebase not ready): a failed work would leave the queue unsent until
          // the next sighting is queued, so try again later instead
          Log.e(TAG, "Sending the queued sightings failed", e)
          return Result.retry()
        }
    report.dropped.forEach { Log.w(TAG, "Dropped sighting ${it.sightingId}: ${it.reason}") }
    return if (report.retryLater) Result.retry() else Result.success()
  }

  companion object {
    private const val TAG = "PendingSightingSend"

    /** Name of the unique work, so only one sender runs at a time. */
    const val WORK_NAME = "send_pending_sightings"

    /**
     * Asks for the queued sightings to be sent once the phone is online. If a send is already
     * scheduled or running, this one runs after it, so a sighting added meanwhile is not missed.
     */
    fun schedule(context: Context) {
      val request =
          OneTimeWorkRequestBuilder<PendingSightingSendWorker>()
              .setConstraints(
                  Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
              )
              .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
              .build()
      WorkManager.getInstance(context)
          .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private fun productionSender(context: Context) =
        PendingSightingSender(
            pending = PendingSightingRepositoryProvider.get(context),
            sightings = SightingRepositoryFirestore(),
            photos = PhotoRepositoryFirestore(context),
            currentUserId = { AuthRepositoryProvider.authRepository.currentUser.value?.uid },
            isRefusal = ::isSightingRefusal,
        )
  }
}
