// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.fakes

import com.android.spotted.model.sighting.SightingStatus
import com.android.spotted.model.trust.SightingNotFoundException
import com.android.spotted.model.trust.SightingNotHiddenException
import com.android.spotted.model.trust.TrustRepository
import com.android.spotted.model.trust.TrustScore
import com.android.spotted.model.trust.Verdict

/**
 * In-memory [TrustRepository] for tests.
 *
 * @param trusts the stored trust of each user, by uid; other users read as [TrustScore.INITIAL].
 * @param reporters the reporter of each sighting that can be judged, by sighting id.
 * @param sightings if set, [confirmSighting], [hideByVotes] and [restoreSighting] also change the
 *   sighting's status there (and find its reporter there), like the single Firestore transaction
 *   does.
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

  /** The reporter's trust just before the penalty of each hidden sighting, by sighting id. */
  private val penalties = mutableMapOf<String, TrustScore>()

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

  /**
   * Hides the sighting [sightingId] as enough votes would: unless it was already judged, its
   * reporter takes a false verdict and its status becomes [SightingStatus.VOTED_FALSE].
   */
  suspend fun hideByVotes(sightingId: String) {
    val reporterId = reporterOf(sightingId) ?: throw SightingNotFoundException(sightingId)
    if (sightingId in appliedVerdicts) return
    sightings?.setSightingStatus(sightingId, SightingStatus.VOTED_FALSE)?.getOrThrow()
    val before = trusts[reporterId] ?: TrustScore.INITIAL
    trusts[reporterId] = before.after(Verdict.FALSE)
    appliedVerdicts[sightingId] = Verdict.FALSE
    penalties[sightingId] = before
  }

  override suspend fun confirmSighting(sightingId: String): Result<Unit> {
    failure?.let {
      return Result.failure(it)
    }
    val reporterId =
        reporterOf(sightingId) ?: return Result.failure(SightingNotFoundException(sightingId))
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

  override suspend fun restoreSighting(sightingId: String): Result<Unit> {
    failure?.let {
      return Result.failure(it)
    }
    val reporterId =
        reporterOf(sightingId) ?: return Result.failure(SightingNotFoundException(sightingId))
    // Removed once restored, so a sighting is restored once
    val beforePenalty =
        penalties[sightingId] ?: return Result.failure(SightingNotHiddenException(sightingId))
    sightings?.setSightingStatus(sightingId, SightingStatus.PENDING)?.onFailure {
      return Result.failure(it)
    }
    trusts[reporterId] = checkNotNull(trusts[reporterId]).withoutPenalty(beforePenalty)
    penalties.remove(sightingId)
    return Result.success(Unit)
  }

  private fun reporterOf(sightingId: String): String? =
      reporters[sightingId]
          ?: sightings?.reportedSightings?.firstOrNull { it.id == sightingId }?.reporterId
}
