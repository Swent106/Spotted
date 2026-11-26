// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.settings

/** State of the Settings screen, filled by [SettingsViewModel]. */
data class SettingsUiState(
    /** True while signing out: the Sign out button is disabled meanwhile. */
    val isLeaving: Boolean = false,
    val errorMessage: String? = null,
)
