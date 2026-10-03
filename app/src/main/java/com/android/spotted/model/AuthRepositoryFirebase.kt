package com.android.spotted.model

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
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

      if (firebaseUser != null) {
        val user = mapFirebaseUser(firebaseUser)!!
        Result.success(user)
      } else {
        Result.failure(Exception("Firebase returned a null user after successful sign in."))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun signOut(): Result<Unit> {
    return try {
      firebaseAuth.signOut()
      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  private fun mapFirebaseUser(firebaseUser: com.google.firebase.auth.FirebaseUser?): User? {
    return if (firebaseUser != null) {
      User(uid = firebaseUser.uid, email = firebaseUser.email ?: "")
    } else {
      null
    }
  }
}
