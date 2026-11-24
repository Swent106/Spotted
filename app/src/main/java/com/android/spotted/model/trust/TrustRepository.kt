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
   * With [restoreSighting], this is the only way an owner changes a trust score: one owner cannot
   * lower a reporter's score. [Verdict.FALSE] comes only from the votes of other users (#463).
   *
   * A sighting counts once: confirming it again, e.g. after dismissing it, only changes its status.
   * Both changes are made or neither is, and two confirmations at the same time are both counted.
   * Fails with [SightingNotFoundException] if the sighting does not exist; only the owner of its
   * alert may confirm it. Never throws; a refused or failed write is a [Result.failure].
   */
  suspend fun confirmSighting(sightingId: String): Result<Unit>

  /**
   * Restores the sighting [sightingId], hidden by the votes of other users: its status goes back to
   * pending, and the false verdict its reporter took is cancelled (see
   * [TrustScore.withoutPenalty]), in the same write. Only the owner of its alert may restore it.
   *
   * A sighting is restored once: it stays judged, so the votes can never hide it again. Fails with
   * [SightingNotFoundException] if the sighting does not exist, and with
   * [SightingNotHiddenException] if it is not hidden by votes. Never throws; a refused or failed
   * write is a [Result.failure].
   */
  suspend fun restoreSighting(sightingId: String): Result<Unit>
}

/** There is no sighting [sightingId] to judge. */
class SightingNotFoundException(val sightingId: String) :
    NoSuchElementException("No sighting $sightingId")

/** The sighting [sightingId] is not hidden by votes, so there is nothing to restore. */
class SightingNotHiddenException(val sightingId: String) :
    IllegalStateException("Sighting $sightingId is not hidden by votes")
