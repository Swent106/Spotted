package com.android.spotted.data.alert

import com.android.spotted.model.Location
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertRepository
import com.android.spotted.model.distanceKm
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AlertRepositoryFirestore(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : AlertRepository {

  // The name of the collection in Firestore where all alerts will be stored
  private val collectionPath = "alerts"

  override fun getNewId(): String {
    // We get a reference to the collection and ask Firestore to generate a random Document ID
    return db.collection(collectionPath).document().id
  }

  override suspend fun publishAlert(alert: Alert): Result<Unit> {
    return try {
      db.collection(collectionPath).document(alert.id).set(alert.toDto()).await()
      Result.success(Unit)
    } catch (e: Exception) {
      // Check if it's a coroutine cancellation
      if (e is kotlinx.coroutines.CancellationException) {
        throw e
      }
      Result.failure(e)
    }
  }

  override suspend fun getOpenAlertsNear(center: Location, radiusKm: Double): Result<List<Alert>> {
    return try {
      val snapshot = db.collection(collectionPath).whereEqualTo("status", "OPEN").get().await()

      val alerts = snapshot.toObjects(AlertDto::class.java).map { it.toDomain() }
      // Filter by distance and sort by lostAtMillis (descending) locally
      val nearbyAlerts =
          alerts
              .filter { center.distanceKm(it.lastKnownLocation) <= radiusKm }
              .sortedWith(compareByDescending<Alert> { it.lostAtMillis }.thenBy { it.id })

      Result.success(nearbyAlerts)
    } catch (e: Exception) {
      if (e is kotlinx.coroutines.CancellationException) {
        throw e
      }
      Result.failure(e)
    }
  }
}
