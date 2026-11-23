// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.moderation

import com.android.spotted.model.moderation.ReportRepository
import com.android.spotted.model.moderation.UserReport
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [ReportRepository] storing each report in `reports/{id}`. The Firestore rules only let a
 * signed-in user create a report as themselves, about someone else; nobody can read, change or
 * delete one from the app.
 */
class ReportRepositoryFirestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : ReportRepository {

  private val reports = firestore.collection(REPORTS_COLLECTION)

  override fun getNewId(): String = reports.document().id

  override suspend fun report(report: UserReport): Result<Unit> {
    if (report.reporterId == report.reportedUserId) {
      return Result.failure(IllegalArgumentException("A user can't report themselves"))
    }
    return try {
      withContext(Dispatchers.IO) {
        Tasks.await(reports.document(report.id).set(toDocument(report)))
      }
      Result.success(Unit)
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: ExecutionException) {
      Result.failure(exception.cause ?: exception)
    } catch (exception: Exception) {
      Result.failure(exception)
    }
  }

  private fun toDocument(report: UserReport): Map<String, Any> =
      mapOf(
          "reporterId" to report.reporterId,
          "reportedUserId" to report.reportedUserId,
          "targetId" to report.targetId,
          "reason" to report.reason.name,
          "createdAtMillis" to report.createdAtMillis,
      )

  private companion object {
    const val REPORTS_COLLECTION = "reports"
  }
}
