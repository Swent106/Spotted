package com.android.spotted.ui.authentification

import com.google.firebase.auth.FirebaseUser

data class SignInUiState(
    val isLoading : Boolean = false,
    val errorMessage: String? = null,
    val user: FirebaseUser? = null,

    )
