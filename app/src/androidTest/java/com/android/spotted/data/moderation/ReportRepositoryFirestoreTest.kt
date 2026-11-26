// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.moderation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.moderation.ReportReason
import com.android.spotted.model.moderation.UserReport
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** [ReportRepositoryFirestore] against the Firestore emulator, with its security rules. */
@RunWith(AndroidJUnit4::class)
class ReportRepositoryFirestoreTest {
  private lateinit var auth: FirebaseAuth
  private lateinit var firestore: FirebaseFirestore
  private lateinit var repository: ReportRepositoryFirestore
  private lateinit var me: String
  private val spammer = "spammer-${UUID.randomUUID()}"

  @Before
  fun setUp() = runTest {
    withContext(Dispatchers.IO) {
      auth = FirebaseAuth.getInstance()
      firestore = FirebaseFirestore.getInstance()
      repository = ReportRepositoryFirestore(firestore)
      me = Tasks.await(auth.signInAnonymously()).user!!.uid
    }
  }

  @After fun signOut() = runTest { withContext(Dispatchers.IO) { auth.signOut() } }

  private fun reportOf(
      reportedUserId: String = spammer,
      reporterId: String = me,
      targetId: String = "sighting-${UUID.randomUUID()}",
  ) =
      UserReport(
          reporterId = reporterId,
          reportedUserId = reportedUserId,
          targetId = targetId,
          reason = ReportReason.FAKE_SIGHTING,
          createdAtMillis = System.currentTimeMillis(),
      )

  /** Asserts that [error] is the rules refusing the request, not any other failure. */
  private fun assertDenied(error: Throwable?) {
    val cause = if (error is ExecutionException) error.cause else error
    assertEquals(
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        (cause as? FirebaseFirestoreException)?.code,
    )
  }

  @Test
  fun report_aboutSomeoneElse_succeeds() = runTest {
    assertTrue(repository.report(reportOf()).isSuccess)
  }

  @Test
  fun reportingTheSameTargetTwice_isDenied() = runTest {
    val report = reportOf()
    assertTrue(repository.report(report).isSuccess)

    assertDenied(repository.report(report.copy(reason = ReportReason.SPAM)).exceptionOrNull())
  }

  @Test
  fun reportingMyself_fails() = runTest {
    val result = repository.report(reportOf(reportedUserId = me))

    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
  }

  @Test
  fun reportingInSomeoneElsesName_isDenied() = runTest {
    val report = reportOf(reporterId = "other-${UUID.randomUUID()}")

    assertDenied(repository.report(report).exceptionOrNull())
  }

  @Test
  fun aSentReport_canBeNeitherReadNorDeleted() = runTest {
    val report = reportOf()
    assertTrue(repository.report(report).isSuccess)

    val document = firestore.collection("reports").document("${me}_${report.targetId}")
    val read = runCatching { withContext(Dispatchers.IO) { Tasks.await(document.get()) } }
    val delete = runCatching { withContext(Dispatchers.IO) { Tasks.await(document.delete()) } }

    assertDenied(read.exceptionOrNull())
    assertDenied(delete.exceptionOrNull())
  }
}
