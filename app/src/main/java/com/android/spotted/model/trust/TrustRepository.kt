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
   * Judges the sighting [sightingId]: applies [verdict] to the trust of its reporter, and returns
   * their new trust.
   *
   * A sighting counts once: its first verdict is final for the trust score, so a second one fails
   * with [VerdictAlreadyAppliedException] and changes nothing. A sighting that does not exist fails
   * with [SightingNotFoundException]. Only the owner of the alert the sighting is linked to may
   * judge it. Two verdicts given at the same time are both applied. Never throws; a refused or
   * failed write is a [Result.failure].
   */
  suspend fun applyVerdict(sightingId: String, verdict: Verdict): Result<TrustScore>

  /**
   * Confirms the sighting [sightingId] (its status becomes confirmed) and, the first time it is
   * judged, applies a [Verdict.CONFIRMED] to the trust of its reporter, in the same write.
   *
   * A sighting counts once: confirming it again, e.g. after dismissing it, only changes its status.
   * Both changes are made or neither is. Fails with [SightingNotFoundException] if the sighting
   * does not exist; only the owner of its alert may confirm it. Never throws; a refused or failed
   * write is a [Result.failure].
   */
  suspend fun confirmSighting(sightingId: String): Result<Unit>
}

/** The sighting [sightingId] was already judged: its verdict counts once and cannot be changed. */
class VerdictAlreadyAppliedException(val sightingId: String) :
    IllegalStateException("The sighting $sightingId was already judged")

/** There is no sighting [sightingId] to judge. */
class SightingNotFoundException(val sightingId: String) :
    NoSuchElementException("No sighting $sightingId")
