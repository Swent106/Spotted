// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.notification

/**
 * A sighting the owner hasn't reviewed yet on one of their open alerts: "Someone saw Milo".
 *
 * @property sightingId The pending sighting.
 * @property alertId The owner's alert it was reported on; opening it leads to "My alert".
 * @property petName Name of the lost pet, taken from the alert.
 * @property sightingPhotoUrl Photo taken by the helper, or null if they sent none.
 * @property seenAtMillis When the pet was seen, in epoch milliseconds.
 */
data class SightingNotification(
    val sightingId: String,
    val alertId: String,
    val petName: String,
    val sightingPhotoUrl: String?,
    val seenAtMillis: Long,
)
