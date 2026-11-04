// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.authentification

import android.content.Context
import android.util.Log
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.spotted.R
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.AuthRepositoryProvider
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Sign-In screen.
 *
 * It drives the Google sign-in flow and exposes its progress as a [SignInUiState]:
 * 1. Shows the Google account picker through the Android [CredentialManager].
 * 2. Extracts the Google ID token from the returned credential.
 * 3. Sends the token to [AuthRepository] to authenticate the user with Firebase.
 * 4. Updates [uiState]: `isLoading` while the sign-in runs, the signed-in user on success, or an
 *    `errorMessage` on failure (cancelled by the user, credential error, repository failure or
 *    unexpected error).
 *
 * The UI only observes [uiState] and calls [signIn] / [clearErrorMsg]; it never changes the state
 * directly.
 *
 * This class was written with the assistance of Claude (Anthropic's AI assistant).
 *
 * Error messages are fixed, user-facing sentences: the exception's own text (Firebase, Credential
 * Manager) is never shown, since it is technical and may be in English only.
 *
 * @property repository The repository used to perform authentication operations. In the app it is
 *   the shared [AuthRepositoryProvider.authRepository] (see [Factory]), so the whole app follows
 *   one signed-in user.
 */
class SignInViewModel(private val repository: AuthRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(SignInUiState())
  val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

  /** Clears the error message in the UI state. */
  fun clearErrorMsg() {
    _uiState.update { it.copy(errorMessage = null) }
  }

  private fun getSignInOptions(context: Context) =
      GetSignInWithGoogleOption.Builder(
              serverClientId = context.getString(R.string.default_web_client_id)
          )
          .build()

  private fun signInRequest(signInOptions: GetSignInWithGoogleOption) =
      GetCredentialRequest.Builder().addCredentialOption(signInOptions).build()

  private suspend fun getCredential(
      context: Context,
      request: GetCredentialRequest,
      credentialManager: CredentialManager,
  ) = credentialManager.getCredential(context, request).credential

  /** Initiates the Google sign-in flow and updates the UI state on success or failure. */
  fun signIn(context: Context, credentialManager: CredentialManager) {
    if (!startLoading()) return

    viewModelScope.launch {
      try {
        val signInOptions = getSignInOptions(context)
        val signInRequest = signInRequest(signInOptions)
        val credential = getCredential(context, signInRequest, credentialManager)

        repository.signInWithGoogle(extractGoogleIdToken(credential)).fold({ user ->
          _uiState.update { it.copy(isLoading = false, user = user, errorMessage = null) }
        }) { failure ->
          showError(SIGN_IN_FAILED, failure)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: GetCredentialCancellationException) {
        showError(SIGN_IN_CANCELLED, e)
      } catch (e: NoCredentialException) {
        showError(NO_GOOGLE_ACCOUNT, e)
      } catch (e: GetCredentialException) {
        // Interrupted, unknown or configuration errors: the user may well have a Google account
        showError(SIGN_IN_FAILED, e)
      } catch (e: Exception) {
        showError(UNEXPECTED_ERROR, e)
      }
    }
  }

  /** Shows [message] to the user; [cause] is only logged, its text never reaches the screen. */
  private fun showError(message: String, cause: Throwable) {
    Log.w(TAG, "Sign-in failed: $message", cause)
    _uiState.update { it.copy(isLoading = false, errorMessage = message, user = null) }
  }

  private fun startLoading(): Boolean {
    var started = false
    _uiState.update { current ->
      started = !current.isLoading
      if (started) current.copy(isLoading = true, errorMessage = null) else current
    }
    return started
  }

  /**
   * Extracts the Google ID token from [credential].
   *
   * @throws IllegalArgumentException if [credential] is not a Google ID token credential.
   */
  private fun extractGoogleIdToken(credential: Credential): String {
    if (
        credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
      return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
    throw IllegalArgumentException("Unexpected credential type: ${credential.type}")
  }

  companion object {
    private const val TAG = "SignInViewModel"

    const val SIGN_IN_CANCELLED = "Sign-in cancelled"
    const val SIGN_IN_FAILED = "Couldn't sign you in. Please try again."
    const val NO_GOOGLE_ACCOUNT =
        "Couldn't reach a Google account on this phone. Add one in Settings, then try again."
    const val UNEXPECTED_ERROR = "Something went wrong. Please try again."

    /** Builds the ViewModel with the app's single shared [AuthRepository]. */
    val Factory: ViewModelProvider.Factory = viewModelFactory {
      initializer { SignInViewModel(AuthRepositoryProvider.authRepository) }
    }
  }
}
