// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.trust

/**
 * Reads the [TrustScore] of users, and is the only way to change it. The score is stored rather
 * than derived from totals, because [TrustScore.after] depends on the order of the verdicts.
 *
 * Any signed-in user can read anyone's trust, so owners can see how reliable a helper is.
 */
interface TrustRepository {
  /**
   * The trust of the user [uid]: [TrustScore.INITIAL] if they have no stored trust yet (none of
   * their sightings has been judged). Never throws; a failed read is a [Result.failure].
   */
  suspend fun getTrust(uid: String): Result<TrustScore>

  /**
   * Confirms the sighting [sightingId] (its status becomes confirmed) and, the first time it is
   * judged, applies a [Verdict.CONFIRMED] to the trust of its reporter, in the same write.
   *
   * This is the only way to change a trust score: in S6 only confirmations move it (#384), so one
   * owner cannot lower a reporter's score. [Verdict.FALSE] will come with the votes of US-T2.
   *
   * A sighting counts once: confirming it again, e.g. after dismissing it, only changes its status.
   * Both changes are made or neither is, and two confirmations at the same time are both counted.
   * Fails with [SightingNotFoundException] if the sighting does not exist; only the owner of its
   * alert may confirm it. Never throws; a refused or failed write is a [Result.failure].
   */
  suspend fun confirmSighting(sightingId: String): Result<Unit>
}

/** There is no sighting [sightingId] to judge. */
class SightingNotFoundException(val sightingId: String) :
    NoSuchElementException("No sighting $sightingId")
