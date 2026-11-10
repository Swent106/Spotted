// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.notifications

import com.android.spotted.model.notification.SightingNotification

/**
 * State of the Notifications screen: the sightings waiting for the owner's review, across all their
 * open alerts. Filled by [NotificationsViewModel].
 *
 * @property items Pending sightings, newest first.
 * @property isLoading True until the first list (or error) for the signed-in user arrives.
 * @property errorMessage Message to show when the list couldn't be loaded, or null.
 * @property destinationAlertId Alert to open in "My alert" once the owner tapped a notification;
 *   the screen navigates, then calls [NotificationsViewModel.onNavigated].
 */
data class NotificationsUiState(
    val items: List<SightingNotification> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val destinationAlertId: String? = null,
) {
  /** True when the list loaded and nothing is waiting for review. */
  val isEmpty: Boolean
    get() = !isLoading && errorMessage == null && items.isEmpty()
}
