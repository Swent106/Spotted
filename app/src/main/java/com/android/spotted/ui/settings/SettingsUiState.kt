// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.settings

/** State of the Settings screen, filled by [SettingsViewModel]. */
data class SettingsUiState(
    /**
     * True while signing out or deleting the account: both buttons are disabled meanwhile, so the
     * two can't run at once.
     */
    val isLeaving: Boolean = false,
    val errorMessage: String? = null,
    /** True while the "Delete your account?" confirmation is shown. */
    val isDeleteConfirmationVisible: Boolean = false,
)
