package com.android.spotted.utils

import android.content.Context
import android.os.CancellationSignal
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreateCredentialRequest
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PrepareGetCredentialResponse
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.util.concurrent.Executor

/** A Google account of the Auth emulator. A fixed [sub] signs in as the same user on every run. */
data class TestAccount(val name: String, val email: String, val sub: String) {
  companion object {
    /** The account the end-to-end tests sign in with unless they choose another one. */
    val DEFAULT =
        TestAccount(name = "E2E Tester", email = "e2e@spotted.test", sub = "e2e-test-account")
  }
}

/**
 * Returns a Google ID token for [account], as the system account chooser would. Changing [account]
 * between two sign-ins switches users.
 */
class TestCredentialManager(account: TestAccount) : CredentialManager {

  /** Set by the test thread and read by the app when the user taps "Sign in with Google". */
  @Volatile var account: TestAccount = account

  private fun response(): GetCredentialResponse {
    val current = account
    val token =
        FakeJwtGenerator.createFakeGoogleIdToken(
            name = current.name,
            email = current.email,
            sub = current.sub,
        )
    return GetCredentialResponse(
        GoogleIdTokenCredential.Builder().setId(current.email).setIdToken(token).build()
    )
  }

  override suspend fun getCredential(
      context: Context,
      request: GetCredentialRequest,
  ): GetCredentialResponse = response()

  override fun getCredentialAsync(
      context: Context,
      request: GetCredentialRequest,
      cancellationSignal: CancellationSignal?,
      executor: Executor,
      callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>,
  ) {
    executor.execute { callback.onResult(response()) }
  }

  override fun getCredentialAsync(
      context: Context,
      pendingGetCredentialHandle: PrepareGetCredentialResponse.PendingGetCredentialHandle,
      cancellationSignal: CancellationSignal?,
      executor: Executor,
      callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>,
  ) = throw UnsupportedOperationException("Not used by the app")

  override fun prepareGetCredentialAsync(
      request: GetCredentialRequest,
      cancellationSignal: CancellationSignal?,
      executor: Executor,
      callback: CredentialManagerCallback<PrepareGetCredentialResponse, GetCredentialException>,
  ) = throw UnsupportedOperationException("Not used by the app")

  override fun createCredentialAsync(
      context: Context,
      request: CreateCredentialRequest,
      cancellationSignal: CancellationSignal?,
      executor: Executor,
      callback: CredentialManagerCallback<CreateCredentialResponse, CreateCredentialException>,
  ) = throw UnsupportedOperationException("Not used by the app")

  override fun clearCredentialStateAsync(
      request: ClearCredentialStateRequest,
      cancellationSignal: CancellationSignal?,
      executor: Executor,
      callback: CredentialManagerCallback<Void?, ClearCredentialException>,
  ) {
    executor.execute { callback.onResult(null) }
  }

  override fun createSettingsPendingIntent() =
      throw UnsupportedOperationException("Not used by the app")
}
