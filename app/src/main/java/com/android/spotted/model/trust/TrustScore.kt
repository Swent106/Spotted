// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.trust

import kotlin.math.pow

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

/**
 * Constants of the trust score: how it moves, and the boundaries between [TrustLevel]s.
 *
 * [INITIAL_SCORE], [GAIN_RATE] and [LOSS_FACTOR] are copied in `firebase/firestore.rules`
 * (`scoreAfter` and `cancelsPenalty` in `trust/{uid}`), which recompute the score and accept only
 * an equal one. Change them in both places, or every verdict will be refused.
 */
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
 * A false verdict comes from the votes of other users (#463), and the alert owner may cancel it by
 * restoring the sighting: see [withoutPenalty].
 *
 * @property score the trust score, from 0 to 1.
 * @property judged number of the user's sightings that have been judged. Must not be negative.
 * @property falseSightings how many of the [judged] sightings count as false: their penalty was not
 *   cancelled. From 0 to [judged].
 * @property penaltiesApplied how many false verdicts were ever applied, cancelled ones included: it
 *   never goes down. At least [falseSightings].
 * @throws IllegalArgumentException if a value is outside the range above.
 */
data class TrustScore(
    val score: Double,
    val judged: Int,
    val falseSightings: Int = 0,
    val penaltiesApplied: Int = falseSightings,
) {
  init {
    require(score in 0.0..1.0) { "score must be between 0 and 1, was $score" }
    require(judged >= 0) { "judged must not be negative, was $judged" }
    require(falseSightings in 0..judged) {
      "falseSightings must be between 0 and judged ($judged), was $falseSightings"
    }
    require(penaltiesApplied >= falseSightings) {
      "penaltiesApplied must be at least falseSightings ($falseSightings), was $penaltiesApplied"
    }
  }

  /** Number of the [judged] sightings that were confirmed. Never goes down. */
  val confirmed: Int
    get() = judged - falseSightings

  /** The trust after one more of the user's sightings is judged [verdict]. */
  fun after(verdict: Verdict): TrustScore =
      when (verdict) {
        Verdict.CONFIRMED ->
            copy(score = score + TrustRules.GAIN_RATE * (1 - score), judged = judged + 1)
        Verdict.FALSE ->
            TrustScore(
                score * TrustRules.LOSS_FACTOR,
                judged + 1,
                falseSightings + 1,
                penaltiesApplied + 1,
            )
      }

  /**
   * The trust once the false verdict applied when the trust was [beforePenalty] is cancelled.
   *
   * Every verdict moves the score along a straight line (`× (1 − GAIN_RATE)` then `+ GAIN_RATE`, or
   * `× LOSS_FACTOR`). Two scores going through the same verdicts keep their gap, multiplied by the
   * slope of each verdict. The penalty opened a gap of `p × (1 − LOSS_FACTOR)`, where p is the
   * score of [beforePenalty], so the score without it is:
   *
   * `score + (1 − GAIN_RATE)^c × LOSS_FACTOR^f × p × (1 − LOSS_FACTOR)`
   *
   * with c the confirmations and f the false verdicts applied since the penalty.
   *
   * This is exact if no other penalty of the user was cancelled in between. Otherwise f also counts
   * the cancelled ones, and p may still hold the penalty of an older one: the result is then a bit
   * lower than the exact score, never higher. The judged and false counts lose the cancelled
   * verdict; [penaltiesApplied] keeps it.
   *
   * `firebase/firestore.rules` (`cancelsPenalty` in `trust/{uid}`) computes the same score.
   *
   * @throws IllegalArgumentException if [beforePenalty] is not an earlier trust of this user with a
   *   penalty applied after it and not cancelled yet.
   */
  fun withoutPenalty(beforePenalty: TrustScore): TrustScore {
    val confirmedSince = confirmed - beforePenalty.confirmed
    val penaltiesSince = penaltiesApplied - beforePenalty.penaltiesApplied - 1
    require(confirmedSince >= 0 && penaltiesSince >= 0 && falseSightings >= 1) {
      "$beforePenalty is not a trust before a penalty of $this"
    }
    val slopes =
        (1 - TrustRules.GAIN_RATE).pow(confirmedSince) * TrustRules.LOSS_FACTOR.pow(penaltiesSince)
    val restored = score + slopes * beforePenalty.score * (1 - TrustRules.LOSS_FACTOR)
    return TrustScore(
        // Never above 1 in exact arithmetic, but the doubles may round just above it
        restored.coerceAtMost(1.0),
        judged - 1,
        falseSightings - 1,
        penaltiesApplied,
    )
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
