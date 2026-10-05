package com.android.spotted.model.auth

import com.android.spotted.data.auth.AuthRepositoryFirebase

object AuthRepositoryProvider {
  /**
   * Singleton instance of the AuthRepository. Use this provider to inject the repository into
   * ViewModels to ensure the entire app shares the same StateFlow and Firebase Auth listeners.
   */
  val authRepository: AuthRepository by lazy { AuthRepositoryFirebase() }
}
