// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.fakes

import com.android.spotted.model.trust.TrustRepository
import com.android.spotted.model.trust.TrustScore

/**
 * In-memory [TrustRepository] for tests.
 *
 * @param trusts the stored trust of each user, by uid; other users read as [TrustScore.INITIAL].
 */
class FakeTrustRepository(trusts: Map<String, TrustScore> = emptyMap()) : TrustRepository {
  private val trusts = trusts.toMutableMap()

  /** If set, every read fails with it. */
  var failure: Throwable? = null

  /** Stores [trust] as the trust of [uid], as if their sightings had been judged. */
  fun setTrust(uid: String, trust: TrustScore) {
    trusts[uid] = trust
  }

  override suspend fun getTrust(uid: String): Result<TrustScore> {
    failure?.let {
      return Result.failure(it)
    }
    return Result.success(trusts[uid] ?: TrustScore.INITIAL)
  }
}
