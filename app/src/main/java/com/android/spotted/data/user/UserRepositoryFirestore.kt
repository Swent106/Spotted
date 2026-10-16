package com.android.spotted.data.user

import com.android.spotted.model.Location
import com.android.spotted.model.user.UserProfile
import com.android.spotted.model.user.UserRepository
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserRepositoryFirestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : UserRepository {
  private val users = firestore.collection(USERS_COLLECTION)

  override suspend fun getUser(uid: String): Result<UserProfile> = firestoreResult {
    withContext(Dispatchers.IO) {
      val snapshot = Tasks.await(users.document(uid).get())
      if (!snapshot.exists()) {
        throw NoSuchElementException("User with uid $uid was not found")
      }

      val document =
          snapshot.toObject(UserProfileDocument::class.java)
              ?: throw IllegalStateException("User document $uid could not be decoded")
      if (document.uid != snapshot.id) {
        throw IllegalStateException("User document ${snapshot.id} has a mismatched uid")
      }
      document.toUserProfile()
    }
  }

  override suspend fun saveUser(user: UserProfile): Result<Unit> = firestoreResult {
    withContext(Dispatchers.IO) {
      Tasks.await(users.document(user.uid).set(UserProfileDocument.from(user)))
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
          }
    }
  }

  private companion object {
    const val USERS_COLLECTION = "users"
  }
}
