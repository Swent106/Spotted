package com.android.spotted.ui.authentification

import android.content.Context
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.spotted.R
import com.android.spotted.data.auth.AuthRepositoryFirebase
import com.android.spotted.model.auth.AuthRepository
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
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
 * 4. Updates [uiState]: `isLoading` while the sign-in runs, the signed-in user on success,
 *    or an `errorMessage` on failure (cancelled by the user, credential error, repository
 *    failure or unexpected error).
 *
 * The UI only observes [uiState] and calls [signIn] / [clearErrorMsg]; it never changes the
 * state directly.
 *
 * This class was written with the assistance of Claude (Anthropic's AI assistant).
 *
 * @property repository The repository used to perform authentication operations.
 */
class SignInViewModel(private val repository: AuthRepository = AuthRepositoryFirebase()) :
    ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    /** Clears the error message in the UI state. */
    fun clearErrorMsg() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun getSignInOptions(context: Context) =
        GetSignInWithGoogleOption.Builder(
            serverClientId = context.getString(R.string.default_web_client_id)
        ).build()

    private fun signInRequest(signInOptions: GetSignInWithGoogleOption) =
        GetCredentialRequest.Builder().addCredentialOption(signInOptions).build()

    private suspend fun getCredential(
        context: Context,
        request: GetCredentialRequest,
        credentialManager: CredentialManager,
    ) = credentialManager.getCredential(context, request).credential

    /** Initiates the Google sign-in flow and updates the UI state on success or failure. */
    fun signIn(context: Context, credentialManager: CredentialManager) {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val signInOptions = getSignInOptions(context)
            val signInRequest = signInRequest(signInOptions)

            try {
                val credential = getCredential(context, signInRequest, credentialManager)

                repository.signInWithGoogle(extractGoogleIdToken(credential)).fold({ _ ->
                    _uiState.update {
                        it.copy(isLoading = false, user = FirebaseAuth.getInstance().currentUser, errorMessage = null)
                    }
                }) { failure ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = failure.localizedMessage,
                            user = null,
                        )
                    }
                }
            } catch (e: GetCredentialCancellationException) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Sign-in cancelled", user = null)
                }
            } catch (e: GetCredentialException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to get credentials: ${e.localizedMessage}",
                        user = null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Unexpected error: ${e.localizedMessage}",
                        user = null,
                    )
                }
            }
        }
    }

    /**
     * Extracts the Google ID token from [credential].
     *
     * @throws IllegalArgumentException if [credential] is not a Google ID token credential.
     */
    private fun extractGoogleIdToken(credential: Credential): String {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw IllegalArgumentException("Unexpected credential type: ${credential.type}")
    }
}