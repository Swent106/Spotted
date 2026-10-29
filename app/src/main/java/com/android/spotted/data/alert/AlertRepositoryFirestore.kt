// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.alert

import android.util.Log
import com.android.spotted.model.Location
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertRepository
import com.android.spotted.model.alert.AlertStatus
import com.android.spotted.model.distanceKm
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
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
      // Sorted by Firestore before the limit, so the newest alerts are the ones kept. Needs the
      // composite index (ownerId, lostAtMillis desc, __name__) of firestore.indexes.json
      val snapshot =
          db.collection(collectionPath)
              .whereEqualTo("ownerId", ownerId)
              .orderBy("lostAtMillis", Query.Direction.DESCENDING)
              .orderBy(FieldPath.documentId())
              .limit(AlertRepository.MY_ALERTS_LIMIT.toLong())
              .get()
              .await()
      Result.success(snapshot.toAlerts())
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
      // Firestore may wrap what the transaction throws: give back the missing alert as it is
      val missing =
          generateSequence<Throwable>(e) { it.cause }.firstOrNull { it is NoSuchElementException }
      Result.failure(missing ?: e)
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
              .toAlerts()
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

      val alerts = snapshot.toAlerts()
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

  /** The alerts of this snapshot, in order. Documents that aren't valid alerts are logged. */
  private fun QuerySnapshot.toAlerts(): List<Alert> = documents.mapNotNull { document ->
    val alert = document.toObject(AlertDto::class.java)?.toDomain()
    if (alert == null) Log.w(TAG, "Ignoring invalid alert ${document.id}")
    alert
  }

  private companion object {
    const val TAG = "AlertRepositoryFirestore"
  }
}
