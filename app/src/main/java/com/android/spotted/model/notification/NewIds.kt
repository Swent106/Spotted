// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.notification

/** The ids of [current] that are not in [alreadyNotified], in order and without duplicates. */
fun newIds(alreadyNotified: Set<String>, current: List<String>): List<String> =
    current.filterNot { it in alreadyNotified }.distinct()
