// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.sighting

import com.android.spotted.model.photo.PhotoRepository

/** A queued sighting that was removed without being sent, and why. */
data class DroppedSighting(val sightingId: String, val reason: String)

/**
 * What one [PendingSightingSender.sendAll] did.
 *
 * @property sent Ids of the sightings sent, in the order they were sent.
 * @property dropped Sightings the server refused: they were removed from the queue, not sent.
 * @property retryLater True when it stopped early (network error, queue unreadable): the sightings
 *   still queued must be sent again later.
 */
data class SendReport(
    val sent: List<String> = emptyList(),
    val dropped: List<DroppedSighting> = emptyList(),
    val retryLater: Boolean = false,
)

/**
 * Sends the sightings waiting in [pending] through [sightings], oldest first.
 *
 * For each one: uploads its photo (if any), reports the sighting with its queued id and
 * [Sighting.seenAtMillis], then removes it from the queue. Since the id is fixed when it is queued,
 * a sighting sent again (e.g. the app was killed before it was removed) writes the same document,
 * so it is never counted twice.
 * - When sending it fails with an error [isRefusal] accepts (the server refuses it), it can never
 *   succeed: it is removed from the queue and listed in [SendReport.dropped] with the reason. This
 *   includes a refused photo: the sighting is dropped rather than sent without it.
 * - Any other error (no network...) stops the sending there, so the order is kept, and the report
 *   asks to [retry later][SendReport.retryLater].
 *
 * - Only the sightings of the signed-in user are sent: the server refuses a sighting reported by
 *   someone else. With nobody signed in, nothing is sent and it asks to retry later; the sightings
 *   of another account stay queued, for when that account signs in again. The user is checked again
 *   before each sighting, so a sign-out or account switch during a run stops it there.
 *
 * Two calls may overlap (a send blocked on the network can outlive a cancelled run): a sighting is
 * then sent twice, which writes the same document.
 *
 * @param currentUserId Uid of the signed-in user, or null when nobody is signed in.
 * @param isRefusal True when [error] means sending again would fail the same way. Injected, so this
 *   class does not depend on Firebase error types.
 */
class PendingSightingSender(
    private val pending: PendingSightingRepository,
    private val sightings: SightingRepository,
    private val photos: PhotoRepository,
    private val currentUserId: () -> String?,
    private val isRefusal: (error: Throwable) -> Boolean,
) {

  suspend fun sendAll(): SendReport {
    // Signed out, every send is refused: keep them all until someone signs in
    val userId = currentUserId() ?: return SendReport(retryLater = true)
    val queue =
        pending.list().getOrElse {
          return SendReport(retryLater = true)
        }
    val sent = mutableListOf<String>()
    val dropped = mutableListOf<DroppedSighting>()
    for (entry in queue.filter { it.sighting.reporterId == userId }) {
      // Another account signed in meanwhile: the server would refuse the rest, which would drop
      // them
      if (currentUserId() != userId) return SendReport(sent, dropped, retryLater = true)
      val id = entry.sighting.id
      val error = send(entry)
      when {
        error == null -> sent += id
        !isRefusal(error) -> return SendReport(sent, dropped, retryLater = true)
      }
      // If it can't be removed, it is sent again next time, which writes the same document
      if (pending.delete(id).isFailure) return SendReport(sent, dropped, retryLater = true)
      // Only reported as dropped once it is really out of the queue
      if (error != null) dropped += DroppedSighting(id, reasonOf(error))
    }
    return SendReport(sent, dropped)
  }

  /** Uploads the photo of [entry] and reports its sighting. Returns the error, or null if sent. */
  private suspend fun send(entry: PendingSighting): Throwable? {
    val sighting = entry.sighting
    var photoUrl = sighting.photoUrl
    if (entry.photoUri != null) {
      photoUrl =
          photos
              .uploadPetPhoto(
                  ownerId = sighting.reporterId,
                  petId = sighting.id,
                  localUri = entry.photoUri,
              )
              .getOrElse {
                return it
              }
    }
    return sightings.reportSighting(sighting.copy(photoUrl = photoUrl)).exceptionOrNull()
  }

  private fun reasonOf(error: Throwable): String =
      error.message?.takeIf { it.isNotBlank() } ?: REFUSED

  companion object {
    const val REFUSED = "Refused by the server"
  }
}
