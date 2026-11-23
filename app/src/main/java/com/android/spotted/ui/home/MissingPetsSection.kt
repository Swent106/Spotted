// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.home

/**
 * Status line of a missing pet's card in "Your pets": "Lost 2 h ago", or only "Missing" when the
 * time is unknown ([nowMillis] is 0 until Home has loaded).
 */
internal fun missingDetail(lostAtMillis: Long, nowMillis: Long): String =
    if (nowMillis <= 0L) "Missing" else "Lost ${timeAgo(lostAtMillis, nowMillis)}"
