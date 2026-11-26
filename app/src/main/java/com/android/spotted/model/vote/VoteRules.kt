// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.vote

import com.android.spotted.model.trust.TrustScore

/**
 * When "This looks false" votes hide a sighting (#463).
 *
 * Each vote weighs the trust score of its voter when they vote (from 0 to 1; only Trusted users,
 * score 0.4 or more, may vote). The sighting is hidden, and its reporter takes a false verdict,
 * when the total of the weights reaches [THRESHOLD]. So several trusted users must agree: one
 * person alone, or a few fake accounts, cannot hide a real sighting.
 *
 * [THRESHOLD] is copied in `firebase/firestore.rules` (`reachesThreshold` in `sightings`, and the
 * vote penalty in `trust/{uid}`). Change it in both places.
 */
object VoteRules {
  /**
   * Total weight that hides a sighting: at least 3 very trusted voters (a score never reaches 1),
   * or 5 voters at the lowest Trusted score (0.4).
   */
  const val THRESHOLD = 2.0

  /** The weight of the votes on a sighting after one more vote from a user with [voterTrust]. */
  fun weightAfter(weightBefore: Double, voterTrust: TrustScore): Double =
      weightBefore + voterTrust.score

  /** Whether votes of this total [weight] hide the sighting. */
  fun reachesThreshold(weight: Double): Boolean = weight >= THRESHOLD
}
