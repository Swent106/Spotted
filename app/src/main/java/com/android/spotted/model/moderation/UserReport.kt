// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.moderation

/** Why a user is reported. */
enum class ReportReason {
  /** A sighting that didn't happen, or a photo of another pet. */
  FAKE_SIGHTING,
  SPAM,
  /** Rude or offensive content. */
  INAPPROPRIATE,
  OTHER,
}

/**
 * A report of a user, for the moderators. Only the app writes it; nobody can read or change it from
 * the app. A user reports a given alert or sighting once.
 *
 * @property reporterId uid of the user who reports.
 * @property reportedUserId uid of the user reported; never [reporterId].
 * @property targetId id of what is reported: the alert or the sighting the reported user made.
 * @property reason why the user is reported.
 * @property createdAtMillis when the report was made, in epoch millis.
 */
data class UserReport(
    val reporterId: String,
    val reportedUserId: String,
    val targetId: String,
    val reason: ReportReason,
    val createdAtMillis: Long,
)
