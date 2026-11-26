// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.fakes

import com.android.spotted.model.moderation.ReportRepository
import com.android.spotted.model.moderation.UserReport

/**
 * In-memory [ReportRepository] for ViewModel tests.
 * - [reports]: every report sent, in order.
 * - [failure]: set it to make [report] fail with it.
 * - Like the real one, reporting yourself fails, and so does reporting the same target twice.
 */
class FakeReportRepository : ReportRepository {
  val reports = mutableListOf<UserReport>()

  var failure: Throwable? = null

  override suspend fun report(report: UserReport): Result<Unit> {
    failure?.let {
      return Result.failure(it)
    }
    if (report.reporterId == report.reportedUserId) {
      return Result.failure(IllegalArgumentException("A user can't report themselves"))
    }
    if (reports.any { it.reporterId == report.reporterId && it.targetId == report.targetId }) {
      return Result.failure(IllegalStateException("Already reported"))
    }
    reports += report
    return Result.success(Unit)
  }
}
