// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.sighting

import android.content.Context
import android.net.Uri
import com.android.spotted.model.sighting.PendingSightingRepository
import java.io.File

/**
 * Gives the [PendingSightingRepository] shared by the whole app: the queue in the app's private
 * files, sent by [PendingSightingSendWorker]. Always one instance, as two
 * [PendingSightingRepositoryFile] on the same folder could overwrite each other.
 */
object PendingSightingRepositoryProvider {
  private const val FOLDER = "pending_sightings"

  @Volatile private var instance: PendingSightingRepository? = null

  fun get(context: Context): PendingSightingRepository =
      instance
          ?: synchronized(this) {
            instance
                ?: context.applicationContext.let { app ->
                  PendingSightingRepositoryFile(
                          directory = File(app.filesDir, FOLDER),
                          openPhoto = { app.contentResolver.openInputStream(Uri.parse(it)) },
                          scheduleSend = { PendingSightingSendWorker.schedule(app) },
                      )
                      .also { instance = it }
                }
          }
}
