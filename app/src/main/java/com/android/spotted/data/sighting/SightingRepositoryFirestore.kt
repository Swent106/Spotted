// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.sighting

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.sighting.Sighting
import com.android.spotted.model.sighting.SightingRepository
import com.android.spotted.model.sighting.SightingStatus
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

class SightingRepositoryFirestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : SightingRepository {
  private val sightings = firestore.collection(SIGHTINGS_COLLECTION)

  override fun getNewId(): String {
    return sightings.document().id
  }

  override suspend fun reportSighting(sighting: Sighting): Result<Unit> = firestoreResult {
    withContext(Dispatchers.IO) {
      Tasks.await(sightings.document(sighting.id).set(SightingDocument.from(sighting)))
      Unit
    }
  }

  override suspend fun getSightingsForAlert(alertId: String): Result<List<Sighting>> =
      firestoreResult {
        withContext(Dispatchers.IO) {
          Tasks.await(alertSightingsQuery(alertId).get()).toSightings()
        }
      }

  override fun observeSightingsForAlert(alertId: String): Flow<Result<List<Sighting>>> =
      callbackFlow {
        // Firestore calls the listener with the current sightings, then after every change
        val registration =
            alertSightingsQuery(alertId).addSnapshotListener { snapshots, error ->
              if (error != null) {
                trySend(Result.failure(error))
                close()
              } else if (snapshots != null) {
                trySend(Result.success(snapshots.toSightings()))
              }
            }
        // Runs when the collector is cancelled, so the listener does not leak
        awaitClose { registration.remove() }
      }
      // Each emission is the full list, so a slow collector only needs the latest one
      .conflate()

  override suspend fun setSightingStatus(sightingId: String, status: SightingStatus): Result<Unit> =
      firestoreResult {
        withContext(Dispatchers.IO) {
          // Only the status is sent: the rules let the alert owner change nothing else
          Tasks.await(sightings.document(sightingId).update(STATUS_FIELD, status.name))
          Unit
        }
      }

  /**
   * Sightings of [alertId], most recent first. Firestore sorts before the limit, so the
   * [MAX_RESULTS] most recent sightings are kept. The rules only allow listing with a limit of at
   * most [MAX_RESULTS], and this query needs the composite index in firebase/firestore.indexes.json
   */
  private fun alertSightingsQuery(alertId: String): Query =
      sightings
          .whereEqualTo("alertId", alertId)
          .orderBy("seenAtMillis", Query.Direction.DESCENDING)
          .orderBy(FieldPath.documentId())
          .limit(MAX_RESULTS)

  /** Invalid documents are skipped instead of being shown with default values. */
  private fun QuerySnapshot.toSightings(): List<Sighting> =
      documents
          .mapNotNull { snapshot ->
            runCatching { snapshot.toObject(SightingDocument::class.java) }
                .getOrNull()
                ?.toSighting(snapshot.id)
          }
          .sortedWith(compareByDescending<Sighting> { it.seenAtMillis }.thenBy { it.id })

  private suspend fun <T> firestoreResult(action: suspend () -> T): Result<T> =
      try {
        Result.success(action())
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        Result.failure(exception)
      }

  private class LocationDocument {
    var latitude: Double = 0.0
    var longitude: Double = 0.0
  }

  private class SightingDocument {
    var id: String = ""
    var reporterId: String = ""
    var location: LocationDocument? = null
    var seenAtMillis: Long = 0L
    var species: String = ""
    var photoUrl: String? = null
    var alertId: String? = null
    var status: String? = null

    /**
     * Returns null when the document is missing required data or has an unknown species or status.
     * Sightings saved before the status existed have none: they count as [SightingStatus.PENDING].
     */
    fun toSighting(documentId: String): Sighting? {
      val savedLocation = location ?: return null
      val savedSpecies = Species.entries.firstOrNull { it.name == species } ?: return null
      val savedStatus =
          status?.let { name ->
            SightingStatus.entries.firstOrNull { it.name == name } ?: return null
          } ?: SightingStatus.PENDING
      if (reporterId.isBlank()) return null
      return Sighting(
          id = documentId,
          reporterId = reporterId,
          location = Location(savedLocation.latitude, savedLocation.longitude),
          seenAtMillis = seenAtMillis,
          species = savedSpecies,
          photoUrl = photoUrl,
          alertId = alertId,
          status = savedStatus,
      )
    }

    companion object {
      fun from(sighting: Sighting): SightingDocument =
          SightingDocument().apply {
            id = sighting.id
            reporterId = sighting.reporterId
            location =
                LocationDocument().apply {
                  latitude = sighting.location.latitude
                  longitude = sighting.location.longitude
                }
            seenAtMillis = sighting.seenAtMillis
            species = sighting.species.name
            photoUrl = sighting.photoUrl
            alertId = sighting.alertId
            status = sighting.status.name
          }
    }
  }

  private companion object {
    const val SIGHTINGS_COLLECTION = "sightings"
    const val MAX_RESULTS = 100L
    const val STATUS_FIELD = "status"
  }
}
