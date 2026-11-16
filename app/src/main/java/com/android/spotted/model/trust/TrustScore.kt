// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.trust

/** How much the sightings reported by a user can be trusted. */
enum class TrustLevel {
  /** Fewer than [TrustRules.MIN_JUDGED_SIGHTINGS] judged sightings: not enough history. */
  NEW,

  /** Score below [TrustRules.TRUSTED_MIN_SCORE]. */
  LOW,

  /** Score from [TrustRules.TRUSTED_MIN_SCORE] to [TrustRules.TRUSTED_MAX_SCORE]. */
  TRUSTED,

  /** Score above [TrustRules.TRUSTED_MAX_SCORE]. */
  VERY_TRUSTED,
}

/** How an alert owner judged one of the user's sightings. */
enum class Verdict {
  CONFIRMED,
  FALSE,
}

/** Constants of the trust score: how it moves, and the boundaries between [TrustLevel]s. */
object TrustRules {
  /** Score of a user with no judged sighting. */
  const val INITIAL_SCORE = 0.3

  /** Share of the gap to 1 that a confirmed sighting closes: trust is earned slowly. */
  const val GAIN_RATE = 0.1

  /** Factor applied to the score by a false sighting: trust is lost fast. */
  const val LOSS_FACTOR = 0.5

  /** A user with fewer judged sightings than this is [TrustLevel.NEW]. */
  const val MIN_JUDGED_SIGHTINGS = 3

  /** Lowest score of a [TrustLevel.TRUSTED] user (inclusive). */
  const val TRUSTED_MIN_SCORE = 0.4

  /** Highest score of a [TrustLevel.TRUSTED] user (inclusive); above is very trusted. */
  const val TRUSTED_MAX_SCORE = 0.75
}

/**
 * The trust of a user, updated after each of their sightings is judged (additive increase,
 * multiplicative decrease):
 * - confirmed: `score + GAIN_RATE × (1 − score)`, a small step towards 1;
 * - false: `score × LOSS_FACTOR`, the score is halved.
 *
 * So about 10 confirmed sightings are needed to become very trusted, and one false sighting is
 * enough to lose it, however long the history. Start from [INITIAL].
 *
 * @property score the trust score, from 0 to 1.
 * @property judged number of the user's sightings that have been judged. Must not be negative.
 * @throws IllegalArgumentException if [score] is outside 0..1 or [judged] is negative.
 */
data class TrustScore(val score: Double, val judged: Int) {
  init {
    require(score in 0.0..1.0) { "score must be between 0 and 1, was $score" }
    require(judged >= 0) { "judged must not be negative, was $judged" }
  }

  /** The trust after one more of the user's sightings is judged [verdict]. */
  fun after(verdict: Verdict): TrustScore {
    val next =
        when (verdict) {
          Verdict.CONFIRMED -> score + TrustRules.GAIN_RATE * (1 - score)
          Verdict.FALSE -> score * TrustRules.LOSS_FACTOR
        }
    return TrustScore(next, judged + 1)
  }

  /** The [TrustLevel] for this trust. */
  val level: TrustLevel
    get() =
        when {
          judged < TrustRules.MIN_JUDGED_SIGHTINGS -> TrustLevel.NEW
          score < TrustRules.TRUSTED_MIN_SCORE -> TrustLevel.LOW
          score <= TrustRules.TRUSTED_MAX_SCORE -> TrustLevel.TRUSTED
          else -> TrustLevel.VERY_TRUSTED
        }

  companion object {
    /** The trust of a user with no judged sighting. */
    val INITIAL = TrustScore(TrustRules.INITIAL_SCORE, 0)
  }
}
