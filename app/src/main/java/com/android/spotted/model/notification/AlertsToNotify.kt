// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.notification

import com.android.spotted.model.alert.Alert

/**
 * The alerts of [current] to notify: those not in [alreadyNotified] (alert ids), never the alerts
 * of [signedInUid], in order and without duplicates.
 */
fun alertsToNotify(
    alreadyNotified: Set<String>,
    current: List<Alert>,
    signedInUid: String,
): List<Alert> =
    current.filter { it.ownerId != signedInUid && it.id !in alreadyNotified }.distinctBy { it.id }
