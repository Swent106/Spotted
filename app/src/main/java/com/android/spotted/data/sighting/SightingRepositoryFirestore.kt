package com.android.spotted.data.sighting

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.sighting.Sighting
import com.android.spotted.model.sighting.SightingRepository
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
          // Firestore sorts before the limit, so the MAX_RESULTS most recent sightings are kept.
          // The rules only allow listing with a limit of at most MAX_RESULTS, and this query
          // needs the composite index in firebase/firestore.indexes.json
          val query =
              sightings
                  .whereEqualTo("alertId", alertId)
                  .orderBy("seenAtMillis", Query.Direction.DESCENDING)
                  .orderBy(FieldPath.documentId())
                  .limit(MAX_RESULTS)
          val snapshots = Tasks.await(query.get())
          // Invalid documents are skipped instead of being shown with default values
          snapshots.documents
              .mapNotNull { snapshot ->
                runCatching { snapshot.toObject(SightingDocument::class.java) }
                    .getOrNull()
                    ?.toSighting(snapshot.id)
              }
              .sortedWith(compareByDescending<Sighting> { it.seenAtMillis }.thenBy { it.id })
        }
      }

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

    /** Returns null when the document is missing required data or has an unknown species. */
    fun toSighting(documentId: String): Sighting? {
      val savedLocation = location ?: return null
      val savedSpecies = Species.entries.firstOrNull { it.name == species } ?: return null
      if (reporterId.isBlank()) return null
      return Sighting(
          id = documentId,
          reporterId = reporterId,
          location = Location(savedLocation.latitude, savedLocation.longitude),
          seenAtMillis = seenAtMillis,
          species = savedSpecies,
          photoUrl = photoUrl,
          alertId = alertId,
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
          }
    }
  }

  private companion object {
    const val SIGHTINGS_COLLECTION = "sightings"
    const val MAX_RESULTS = 100L
  }
}
