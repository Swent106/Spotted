// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.user

import com.android.spotted.model.Location
import com.android.spotted.model.user.UserProfile
import com.android.spotted.model.user.UserRepository
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

class UserRepositoryFirestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : UserRepository {
  private val users = firestore.collection(USERS_COLLECTION)

  override suspend fun getUser(uid: String): Result<UserProfile> = firestoreResult {
    withContext(Dispatchers.IO) { Tasks.await(users.document(uid).get()).toUserProfile(uid) }
  }

  override fun observeUser(uid: String): Flow<Result<UserProfile>> = callbackFlow {
    // Firestore calls the listener with the current profile, then after every change
    val registration =
        users.document(uid).addSnapshotListener(Dispatchers.Default.asExecutor()) { snapshot, error
          ->
          if (error != null) {
            trySend(Result.failure(error))
            close()
          } else if (snapshot != null) {
            trySend(runCatching { snapshot.toUserProfile(uid) })
          }
        }
    // Runs when the collector is cancelled, so the listener does not leak
    awaitClose { registration.remove() }
  }
      .conflate()

  private fun DocumentSnapshot.toUserProfile(uid: String): UserProfile {
    if (!exists()) {
      throw NoSuchElementException("User with uid $uid was not found")
    }
    val document =
        toObject(UserProfileDocument::class.java)
            ?: throw IllegalStateException("User document $uid could not be decoded")
    if (document.uid != id) {
      throw IllegalStateException("User document $id has a mismatched uid")
    }
    return document.toUserProfile()
  }

  /**
   * Saves the whole profile. Its `createdAt` is kept as it was, or set to the server time on the
   * first save (and for profiles saved before it existed): the vote rules read it to require an
   * account at least 7 days old, and the Firestore rules never let it move earlier.
   */
  override suspend fun saveUser(user: UserProfile): Result<Unit> = firestoreResult {
    withContext(Dispatchers.IO) {
      val profile = users.document(user.uid)
      val createdAt = Tasks.await(profile.get()).getTimestamp(CREATED_AT_FIELD)
      Tasks.await(profile.set(UserProfileDocument.from(user).also { it.createdAt = createdAt }))
    }
  }

  override suspend fun deleteUser(uid: String): Result<Unit> = firestoreResult {
    withContext(Dispatchers.IO) {
      Tasks.await(users.document(uid).delete())
      Unit
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

  private class UserProfileDocument {
    var uid: String = ""
    var name: String = ""
    var phone: String = ""
    var email: String = ""
    var homeArea: String = ""
    // Null in profiles saved before the home pin was stored
    var homeLatitude: Double? = null
    var homeLongitude: Double? = null
    var alertRadiusKm: Int = UserProfile.DEFAULT_ALERT_RADIUS_KM
    // Empty in profiles saved before the username was asked for
    var username: String = ""
    // When the account was created; null is written as the server time
    @ServerTimestamp var createdAt: Timestamp? = null

    fun toUserProfile() =
        UserProfile(
            uid = uid,
            name = name,
            phone = phone,
            email = email,
            homeArea = homeArea,
            homeLocation =
                homeLatitude?.let { latitude -> homeLongitude?.let { Location(latitude, it) } },
            alertRadiusKm = alertRadiusKm,
            username = username,
        )

    companion object {
      fun from(user: UserProfile) =
          UserProfileDocument().apply {
            uid = user.uid
            name = user.name
            phone = user.phone
            email = user.email
            homeArea = user.homeArea
            homeLatitude = user.homeLocation?.latitude
            homeLongitude = user.homeLocation?.longitude
            alertRadiusKm = user.alertRadiusKm
            username = user.username
          }
    }
  }

  private companion object {
    const val USERS_COLLECTION = "users"
    const val CREATED_AT_FIELD = "createdAt"
  }
}
