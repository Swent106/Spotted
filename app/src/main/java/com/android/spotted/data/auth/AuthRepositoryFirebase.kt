package com.android.spotted.data.auth

import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class AuthRepositoryFirebase(private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()) :
    AuthRepository {

  private val _currentUser = MutableStateFlow<User?>(mapFirebaseUser(firebaseAuth.currentUser))
  override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

  init {
    firebaseAuth.addAuthStateListener { auth ->
      _currentUser.value = mapFirebaseUser(auth.currentUser)
    }
  }

  override suspend fun signInWithGoogle(idToken: String): Result<User> {
    return try {
      val credential = GoogleAuthProvider.getCredential(idToken, null)
      val authResult = firebaseAuth.signInWithCredential(credential).await()
      val firebaseUser = authResult.user

      firebaseUser?.let {
        val user = mapFirebaseUser(it)
        if (user != null) {
          Result.success(user)
        } else {
          Result.failure(Exception("Could not map FirebaseUser to User"))
        }
      } ?: Result.failure(Exception("Firebase returned a null user after successful sign in."))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun signOut(): Result<Unit> {
    return try {
      firebaseAuth.signOut()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  private fun mapFirebaseUser(firebaseUser: FirebaseUser?): User? {
    return firebaseUser?.let {
      // Use email if available, otherwise use a placeholder or handle the lack of email.
      // Since Google Sign-in usually provides an email, falling back to "No email provided" is
      // safer than an empty string.
      val email = it.email ?: "No email provided"
      User(uid = it.uid, email = email)
    }
  }
}
