// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.fakes

import com.android.spotted.model.moderation.ReportRepository
import com.android.spotted.model.moderation.UserReport

/**
 * In-memory [ReportRepository] for ViewModel tests.
 * - [reports]: every report sent, in order.
 * - [failure]: set it to make [report] fail with it.
 * - Like the real one, reporting yourself fails.
 */
class FakeReportRepository : ReportRepository {
  private var nextId = 0

  val reports = mutableListOf<UserReport>()

  var failure: Throwable? = null

  override fun getNewId(): String = "report-${nextId++}"

  override suspend fun report(report: UserReport): Result<Unit> {
    failure?.let {
      return Result.failure(it)
    }
    if (report.reporterId == report.reportedUserId) {
      return Result.failure(IllegalArgumentException("A user can't report themselves"))
    }
    reports += report
    return Result.success(Unit)
  }
}
