package com.android.spotted.data.alert

import com.android.spotted.model.Location
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertRepository
import com.android.spotted.model.alert.AlertStatus
import com.android.spotted.model.distanceKm
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AlertRepositoryFirestore(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val now: () -> Long = System::currentTimeMillis,
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

  override suspend fun getAlertById(id: String): Result<Alert?> {
    return try {
      val snapshot = db.collection(collectionPath).document(id).get().await()
      if (!snapshot.exists()) {
        Result.success(null)
      } else {
        val alert =
            snapshot.toObject(AlertDto::class.java)?.toDomain()
                ?: throw IllegalStateException("Alert $id contains invalid data")
        Result.success(alert)
      }
    } catch (e: Exception) {
      if (e is kotlinx.coroutines.CancellationException) {
        throw e
      }
      Result.failure(e)
    }
  }

  override suspend fun getMyAlerts(ownerId: String): Result<List<Alert>> {
    return try {
      val snapshot =
          db.collection(collectionPath).whereEqualTo("ownerId", ownerId).limit(100).get().await()
      val alerts =
          snapshot
              .toObjects(AlertDto::class.java)
              .mapNotNull { it.toDomain() }
              .sortedWith(compareByDescending<Alert> { it.lostAtMillis }.thenBy { it.id })
      Result.success(alerts)
    } catch (e: Exception) {
      if (e is kotlinx.coroutines.CancellationException) {
        throw e
      }
      Result.failure(e)
    }
  }

  override suspend fun closeAlert(alertId: String): Result<Unit> {
    return try {
      val ref = db.collection(collectionPath).document(alertId)
      // Read and write in one transaction so two concurrent closes keep the first closing time
      // Transactions allow you to run a series of operations that do not change any data until the
      // transaction is committed.
      db.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            if (!snapshot.exists()) throw NoSuchElementException("No alert with id $alertId")
            // Closing is one-way: an already closed alert is left untouched
            if (snapshot.getString("status") != AlertStatus.CLOSED.name) {
              transaction.update(
                  ref,
                  mapOf("status" to AlertStatus.CLOSED.name, "closedAtMillis" to now()),
              )
            }
          }
          .await()
      Result.success(Unit)
    } catch (e: Exception) {
      if (e is kotlinx.coroutines.CancellationException) {
        throw e
      }
      Result.failure(e)
    }
  }

  override suspend fun getOpenAlertsByOwner(ownerId: String): Result<List<Alert>> {
    return try {
      // Two equality filters: served by Firestore's single-field indexes, no composite index
      val snapshot =
          db.collection(collectionPath)
              .whereEqualTo("ownerId", ownerId)
              .whereEqualTo("status", "OPEN")
              .limit(100)
              .get()
              .await()
      val alerts =
          snapshot
              .toObjects(AlertDto::class.java)
              .mapNotNull { it.toDomain() }
              .sortedWith(compareByDescending<Alert> { it.lostAtMillis }.thenBy { it.id })
      Result.success(alerts)
    } catch (e: Exception) {
      if (e is kotlinx.coroutines.CancellationException) {
        throw e
      }
      Result.failure(e)
    }
  }

  override suspend fun deleteAlertsByOwner(ownerId: String): Result<Unit> {
    return try {
      // The rules only allow listing up to 100 alerts at a time
      val snapshot =
          db.collection(collectionPath).whereEqualTo("ownerId", ownerId).limit(100).get().await()
      snapshot.documents.forEach { it.reference.delete().await() }
      Result.success(Unit)
    } catch (e: Exception) {
      if (e is kotlinx.coroutines.CancellationException) {
        throw e
      }
      Result.failure(e)
    }
  }

  override suspend fun getOpenAlertsNear(center: Location, radiusKm: Double): Result<List<Alert>> {
    return try {
      val snapshot =
          db.collection(collectionPath).whereEqualTo("status", "OPEN").limit(100).get().await()

      val alerts = snapshot.toObjects(AlertDto::class.java).mapNotNull { it.toDomain() }
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
