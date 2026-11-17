// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.trust

import com.android.spotted.model.trust.TrustRepository
import com.android.spotted.model.trust.TrustScore
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [TrustRepository] reading `trust/{uid}` documents (`score`, `judged`). A user without a document
 * has no judged sighting yet and reads as [TrustScore.INITIAL].
 */
class TrustRepositoryFirestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : TrustRepository {
  private val trust = firestore.collection(TRUST_COLLECTION)

  override suspend fun getTrust(uid: String): Result<TrustScore> =
      try {
        withContext(Dispatchers.IO) {
          val document = Tasks.await(trust.document(uid).get())
          val trustScore =
              if (!document.exists()) {
                TrustScore.INITIAL
              } else {
                val score =
                    checkNotNull(document.getDouble(SCORE_FIELD)) { "trust/$uid has no score" }
                val judged =
                    checkNotNull(document.getLong(JUDGED_FIELD)) { "trust/$uid has no judged" }
                TrustScore(score, judged.toInt())
              }
          Result.success(trustScore)
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: ExecutionException) {
        Result.failure(exception.cause ?: exception)
      } catch (exception: Exception) {
        Result.failure(exception)
      }

  private companion object {
    const val TRUST_COLLECTION = "trust"
    const val SCORE_FIELD = "score"
    const val JUDGED_FIELD = "judged"
  }
}
