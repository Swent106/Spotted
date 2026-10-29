package com.android.spotted.ui.authentification

import android.content.Context
import androidx.credentials.CredentialManager

/**
 * Gives the [CredentialManager] the Sign in screen uses: the system one by default. Instrumented
 * tests replace [create] with one that returns a test Google token, since the system account
 * chooser can't be driven by a test.
 */
object CredentialManagerProvider {
  @Volatile var create: (Context) -> CredentialManager = { CredentialManager.create(it) }
}
