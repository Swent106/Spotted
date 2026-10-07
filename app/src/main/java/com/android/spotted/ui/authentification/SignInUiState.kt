package com.android.spotted.ui.authentification

import com.android.spotted.model.auth.User


data class SignInUiState(
    val isLoading : Boolean = false,
    val errorMessage: String? = null,
    val user: User? = null,

    )
