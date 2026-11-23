// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.moderation

import com.android.spotted.model.fakes.FakeReportRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeReportRepositoryTest {

  private val repository = FakeReportRepository()

  private fun reportOf(reportedUserId: String, reporterId: String = "alice") =
      UserReport(
          id = repository.getNewId(),
          reporterId = reporterId,
          reportedUserId = reportedUserId,
          targetId = "sighting-1",
          reason = ReportReason.FAKE_SIGHTING,
          createdAtMillis = 1_000L,
      )

  @Test
  fun getNewId_isUnique() {
    assertNotEquals(repository.getNewId(), repository.getNewId())
  }

  @Test
  fun report_isKept_inOrder() = runTest {
    val first = reportOf("spammer")
    val second = reportOf("troll")

    assertTrue(repository.report(first).isSuccess)
    assertTrue(repository.report(second).isSuccess)

    assertEquals(listOf(first, second), repository.reports)
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
