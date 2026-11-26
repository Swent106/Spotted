// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.moderation

/** Sends [UserReport]s to the moderators. Reports can be sent, never read back from the app. */
interface ReportRepository {

  /**
   * Sends [report]. A user reports a given target once: reporting the same target again fails.
   * Reporting yourself fails with [IllegalArgumentException]. Never throws: every failure is a
   * [Result.failure].
   */
  suspend fun report(report: UserReport): Result<Unit>
}
