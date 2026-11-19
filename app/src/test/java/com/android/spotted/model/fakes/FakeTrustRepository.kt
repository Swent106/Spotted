// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.fakes

import com.android.spotted.model.sighting.SightingStatus
import com.android.spotted.model.trust.SightingNotFoundException
import com.android.spotted.model.trust.TrustRepository
import com.android.spotted.model.trust.TrustScore
import com.android.spotted.model.trust.Verdict

/**
 * In-memory [TrustRepository] for tests.
 *
 * @param trusts the stored trust of each user, by uid; other users read as [TrustScore.INITIAL].
 * @param reporters the reporter of each sighting that can be judged, by sighting id.
 * @param sightings if set, [confirmSighting] also confirms the sighting there (and finds its
 *   reporter there), like the single Firestore transaction does.
 */
class FakeTrustRepository(
    trusts: Map<String, TrustScore> = emptyMap(),
    reporters: Map<String, String> = emptyMap(),
    private val sightings: FakeSightingRepository? = null,
) : TrustRepository {
  private val trusts = trusts.toMutableMap()
  private val reporters = reporters.toMutableMap()

  /** The verdict applied to each judged sighting, by sighting id. */
  val verdicts: Map<String, Verdict>
    get() = appliedVerdicts.toMap()

  private val appliedVerdicts = mutableMapOf<String, Verdict>()

  /** If set, every read and write fails with it. */
  var failure: Throwable? = null

  /** Stores [trust] as the trust of [uid], as if their sightings had been judged. */
  fun setTrust(uid: String, trust: TrustScore) {
    trusts[uid] = trust
  }

  /** Makes the sighting [sightingId], reported by [reporterId], one that can be judged. */
  fun addSighting(sightingId: String, reporterId: String) {
    reporters[sightingId] = reporterId
  }

  override suspend fun getTrust(uid: String): Result<TrustScore> {
    failure?.let {
      return Result.failure(it)
    }
    return Result.success(trusts[uid] ?: TrustScore.INITIAL)
  }

  override suspend fun confirmSighting(sightingId: String): Result<Unit> {
    failure?.let {
      return Result.failure(it)
    }
    val reporterId =
        reporters[sightingId]
            ?: sightings?.reportedSightings?.firstOrNull { it.id == sightingId }?.reporterId
            ?: return Result.failure(SightingNotFoundException(sightingId))
    // The status first: if it fails, the trust is left as it was (all or nothing)
    sightings?.setSightingStatus(sightingId, SightingStatus.CONFIRMED)?.onFailure {
      return Result.failure(it)
    }
    if (sightingId !in appliedVerdicts) {
      trusts[reporterId] = (trusts[reporterId] ?: TrustScore.INITIAL).after(Verdict.CONFIRMED)
      appliedVerdicts[sightingId] = Verdict.CONFIRMED
    }
    return Result.success(Unit)
  }
}
