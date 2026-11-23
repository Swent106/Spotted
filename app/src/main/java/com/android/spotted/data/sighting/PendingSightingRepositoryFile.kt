// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.sighting

import android.net.Uri
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.sighting.AnimalActivity
import com.android.spotted.model.sighting.AnimalCondition
import com.android.spotted.model.sighting.AnimalReaction
import com.android.spotted.model.sighting.CollarStatus
import com.android.spotted.model.sighting.PendingSighting
import com.android.spotted.model.sighting.PendingSightingRepository
import com.android.spotted.model.sighting.PlaceType
import com.android.spotted.model.sighting.Sighting
import com.android.spotted.model.sighting.SightingStatus
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * [PendingSightingRepository] that keeps the queue in a JSON file in [directory] and the photo
 * copies next to it, so both survive the app being closed.
 *
 * Every write replaces the whole file through a temporary file and an atomic rename, so a crash
 * leaves either the old queue or the new one, never half a file. Calls are serialized by a mutex:
 * use one instance per process, or two instances could overwrite each other.
 *
 * @param directory Folder holding `queue.json` and the `photos/` copies.
 * @param openPhoto Opens the photo at a URI given to [add] (Photo Picker `content://`, `file://`).
 * @param scheduleSend Asks for the queue to be sent once the network is back; called on each [add].
 */
class PendingSightingRepositoryFile(
    private val directory: File,
    private val openPhoto: (uri: String) -> InputStream?,
    private val scheduleSend: () -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PendingSightingRepository {

  private val mutex = Mutex()
  private val queueFile = File(directory, QUEUE_FILE)
  private val photosDirectory = File(directory, PHOTOS_FOLDER)

  /** The queued sightings, or why the file can't be read. Null until it was read once. */
  private val queueState = MutableStateFlow<Result<List<PendingSighting>>?>(null)

  override suspend fun add(sighting: Sighting, photoUri: String?): Result<Unit> = locked {
    val queue = readQueue()
    val copy = photoUri?.let { copyPhoto(sighting.id, it) }
    if (copy == null) photoFile(sighting.id).delete()
    val entry = PendingSighting(sighting, copy?.let { Uri.fromFile(it).toString() })
    val index = queue.indexOfFirst { it.sighting.id == sighting.id }
    val updated =
        if (index >= 0) queue.toMutableList().apply { set(index, entry) } else queue + entry
    try {
      // Scheduled before the write, so a sighting is never queued without a send: if the app is
      // killed in between, the send finds the old queue. The sender can't read the queue before
      // the write is done, as it waits for the same mutex.
      scheduleSend()
      writeQueue(updated)
    } catch (exception: Exception) {
      if (index < 0) copy?.delete()
      throw exception
    }
  }

  override suspend fun list(): Result<List<PendingSighting>> = locked { readQueue() }

  override suspend fun delete(sightingId: String): Result<Unit> = locked {
    val queue = readQueue()
    val updated = queue.filterNot { it.sighting.id == sightingId }
    if (updated.size != queue.size) writeQueue(updated)
    // False when there is no photo (normal) or it can't be deleted: the sighting is still removed,
    // a photo left behind only takes space. Failing here would make the sender send it again.
    photoFile(sightingId).delete()
  }

  override fun observeQueue(): Flow<Result<List<PendingSighting>>> = flow {
    // Reading the queue sets it, or the failure if it can't be read
    if (queueState.value == null) list()
    emitAll(queueState.filterNotNull())
  }

  /** Runs [action] on [ioDispatcher] under the mutex, returning its errors as a failure. */
  private suspend fun <T> locked(action: () -> T): Result<T> =
      try {
        withContext(ioDispatcher) { mutex.withLock { Result.success(action()) } }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        Result.failure(exception)
      }

  private fun readQueue(): List<PendingSighting> {
    val queue =
        try {
          if (!queueFile.exists()) emptyList()
          else {
            val array = JSONArray(queueFile.readText())
            List(array.length()) { array.getJSONObject(it).toPendingSighting() }
          }
        } catch (exception: Exception) {
          queueState.value = Result.failure(exception)
          throw exception
        }
    queueState.value = Result.success(queue)
    return queue
  }

  private fun writeQueue(queue: List<PendingSighting>) {
    directory.mkdirs()
    val temporary = File(directory, "$QUEUE_FILE.tmp")
    temporary.writeText(JSONArray(queue.map { it.toJson() }).toString())
    Files.move(
        temporary.toPath(),
        queueFile.toPath(),
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING,
    )
    queueState.value = Result.success(queue)
  }

  /** Copies the photo at [uri] to the photo file of [sightingId], through a temporary file. */
  private fun copyPhoto(sightingId: String, uri: String): File {
    photosDirectory.mkdirs()
    val destination = photoFile(sightingId)
    val temporary = File(photosDirectory, "$sightingId.tmp")
    val input = openPhoto(uri) ?: throw FileNotFoundException("Can't open the photo $uri")
    input.use { source -> temporary.outputStream().use { source.copyTo(it) } }
    Files.move(
        temporary.toPath(),
        destination.toPath(),
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING,
    )
    return destination
  }

  private fun photoFile(sightingId: String) = File(photosDirectory, "$sightingId.jpg")

  companion object {
    private const val QUEUE_FILE = "queue.json"
    private const val PHOTOS_FOLDER = "photos"
  }
}

// JSON mapping. Enums are stored by name: their KDoc says names never change.

private const val SIGHTING = "sighting"
private const val PHOTO_URI = "photoUri"

private fun PendingSighting.toJson(): JSONObject =
    JSONObject().put(SIGHTING, sighting.toJson()).putOpt(PHOTO_URI, photoUri)

private fun JSONObject.toPendingSighting() =
    PendingSighting(
        sighting = getJSONObject(SIGHTING).toSighting(),
        photoUri = optionalString(PHOTO_URI),
    )

private fun Sighting.toJson(): JSONObject =
    JSONObject()
        .put("id", id)
        .put("reporterId", reporterId)
        .put("latitude", location.latitude)
        .put("longitude", location.longitude)
        .put("seenAtMillis", seenAtMillis)
        .put("species", species.name)
        .putOpt("photoUrl", photoUrl)
        .putOpt("alertId", alertId)
        .put("status", status.name)
        .putOpt("headingDegrees", headingDegrees)
        .put("activities", names(activities))
        .put("conditions", names(conditions))
        .put("reactions", names(reactions))
        .put("placeType", placeType.name)
        .put("collar", collar.name)

private fun JSONObject.toSighting() =
    Sighting(
        id = getString("id"),
        reporterId = getString("reporterId"),
        location = Location(getDouble("latitude"), getDouble("longitude")),
        seenAtMillis = getLong("seenAtMillis"),
        species = Species.valueOf(getString("species")),
        photoUrl = optionalString("photoUrl"),
        alertId = optionalString("alertId"),
        status = SightingStatus.valueOf(getString("status")),
        headingDegrees = if (isNull("headingDegrees")) null else getInt("headingDegrees"),
        activities = enumSet("activities") { AnimalActivity.valueOf(it) },
        conditions = enumSet("conditions") { AnimalCondition.valueOf(it) },
        reactions = enumSet("reactions") { AnimalReaction.valueOf(it) },
        placeType = PlaceType.valueOf(getString("placeType")),
        collar = CollarStatus.valueOf(getString("collar")),
    )

private fun names(values: Set<Enum<*>>) = JSONArray(values.map { it.name })

/** The string at [key], or null when it is missing or null (`isNull` is true for both). */
private fun JSONObject.optionalString(key: String): String? =
    if (isNull(key)) null else getString(key)

private fun <T> JSONObject.enumSet(key: String, valueOf: (String) -> T): Set<T> {
  val array = getJSONArray(key)
  return List(array.length()) { valueOf(array.getString(it)) }.toSet()
}
