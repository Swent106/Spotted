// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.moderation

import com.android.spotted.model.fakes.FakeReportRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeReportRepositoryTest {

  private val repository = FakeReportRepository()

  private fun reportOf(
      reportedUserId: String,
      targetId: String = "sighting-1",
      reporterId: String = "alice",
  ) =
      UserReport(
          reporterId = reporterId,
          reportedUserId = reportedUserId,
          targetId = targetId,
          reason = ReportReason.FAKE_SIGHTING,
          createdAtMillis = 1_000L,
      )

  @Test
  fun report_isKept_inOrder() = runTest {
    val first = reportOf("spammer", targetId = "sighting-1")
    val second = reportOf("troll", targetId = "alert-1")

    assertTrue(repository.report(first).isSuccess)
    assertTrue(repository.report(second).isSuccess)

    assertEquals(listOf(first, second), repository.reports)
  }

  @Test
  fun reportingTheSameTargetTwice_fails_butAnotherUserCanReportIt() = runTest {
    assertTrue(repository.report(reportOf("spammer")).isSuccess)

    assertTrue(repository.report(reportOf("spammer")).isFailure)
    assertTrue(repository.report(reportOf("spammer", reporterId = "bob")).isSuccess)
    assertEquals(2, repository.reports.size)
  }

  @Test
  fun reportingYourself_fails_andIsNotKept() = runTest {
    val result = repository.report(reportOf(reportedUserId = "alice"))

    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    assertTrue(repository.reports.isEmpty())
  }

  @Test
  fun failure_makesReportFail_andNothingIsKept() = runTest {
    repository.failure = IllegalStateException("offline")

    assertTrue(repository.report(reportOf("spammer")).isFailure)
    assertTrue(repository.reports.isEmpty())
  }
}
