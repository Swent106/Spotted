// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.trust

/**
 * Reads the [TrustScore] of users. The score is stored rather than derived from totals, because
 * [TrustScore.after] depends on the order of the verdicts.
 *
 * Any signed-in user can read anyone's trust, so owners can see how reliable a helper is.
 */
interface TrustRepository {
  /**
   * The trust of the user [uid]: [TrustScore.INITIAL] if they have no stored trust yet (none of
   * their sightings has been judged). Never throws; a failed read is a [Result.failure].
   */
  suspend fun getTrust(uid: String): Result<TrustScore>
}
