// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.moderation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.moderation.ReportReason
import com.android.spotted.model.moderation.UserReport
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertNotEquals
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
      createdAtMillis: Long = System.currentTimeMillis(),
  ) =
      UserReport(
          id = repository.getNewId(),
          reporterId = reporterId,
          reportedUserId = reportedUserId,
          targetId = "sighting-${UUID.randomUUID()}",
          reason = ReportReason.FAKE_SIGHTING,
          createdAtMillis = createdAtMillis,
      )

  @Test
  fun getNewId_isUnique() {
    assertNotEquals(repository.getNewId(), repository.getNewId())
  }

  @Test
  fun report_aboutSomeoneElse_succeeds() = runTest {
    assertTrue(repository.report(reportOf()).isSuccess)
  }

  @Test
  fun reportingMyself_fails() = runTest {
    val result = repository.report(reportOf(reportedUserId = me))

    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
  }

  @Test
  fun reportingInSomeoneElsesName_isDenied() = runTest {
    assertTrue(repository.report(reportOf(reporterId = "other-${UUID.randomUUID()}")).isFailure)
  }

  @Test
  fun aSentReport_canBeNeitherReadNorDeleted() = runTest {
    val report = reportOf()
    assertTrue(repository.report(report).isSuccess)

    val document = firestore.collection("reports").document(report.id)
    val read = runCatching { withContext(Dispatchers.IO) { Tasks.await(document.get()) } }
    val delete = runCatching { withContext(Dispatchers.IO) { Tasks.await(document.delete()) } }

    assertTrue(read.isFailure)
    assertTrue(delete.isFailure)
  }
}
