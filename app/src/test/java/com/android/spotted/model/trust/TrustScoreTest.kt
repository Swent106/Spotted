// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.trust

import com.android.spotted.model.trust.TrustRules.GAIN_RATE
import com.android.spotted.model.trust.TrustRules.INITIAL_SCORE
import com.android.spotted.model.trust.TrustRules.LOSS_FACTOR
import com.android.spotted.model.trust.TrustRules.MIN_JUDGED_SIGHTINGS
import com.android.spotted.model.trust.TrustRules.TRUSTED_MAX_SCORE
import com.android.spotted.model.trust.TrustRules.TRUSTED_MIN_SCORE
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustScoreTest {

  // ===================== Updates =====================

  @Test
  fun newUser_startsAtInitialScore_andIsNew() {
    val trust = TrustScore.INITIAL

    assertEquals(INITIAL_SCORE, trust.score, EPS)
    assertEquals(0, trust.judged)
    assertEquals(TrustLevel.NEW, trust.level)
  }

  @Test
  fun confirmed_closesPartOfTheGapToOne() {
    val trust = TrustScore(0.5, 4).after(Verdict.CONFIRMED)

    assertEquals(0.5 + GAIN_RATE * 0.5, trust.score, EPS)
    assertEquals(5, trust.judged)
  }

  @Test
  fun falseReport_multipliesTheScore() {
    val trust = TrustScore(0.5, 4).after(Verdict.FALSE)

    assertEquals(0.5 * LOSS_FACTOR, trust.score, EPS)
    assertEquals(5, trust.judged)
  }

  @Test
  fun falseReport_losesMoreThanConfirmedGains() {
    val start = TrustScore(0.5, 4)
    val gain = start.after(Verdict.CONFIRMED).score - start.score
    val loss = start.score - start.after(Verdict.FALSE).score

    assertTrue(loss > gain)
  }

  @Test
  fun scoreStaysWithinBounds_atTheExtremes() {
    assertEquals(1.0, TrustScore(1.0, 5).after(Verdict.CONFIRMED).score, EPS)
    assertEquals(0.0, TrustScore(0.0, 5).after(Verdict.FALSE).score, EPS)
  }

  // ===================== Histories =====================

  @Test
  fun threeConfirmed_isTrusted_notYetVeryTrusted() {
    val trust = TrustScore.INITIAL.afterAll(Verdict.CONFIRMED, times = 3)

    assertEquals(1 - 0.7 * 0.9 * 0.9 * 0.9, trust.score, EPS) // ≈ 0.49
    assertEquals(TrustLevel.TRUSTED, trust.level)
  }

  @Test
  fun allConfirmed_becomesVeryTrusted_afterTen() {
    val nine = TrustScore.INITIAL.afterAll(Verdict.CONFIRMED, times = 9)
    val ten = nine.after(Verdict.CONFIRMED)

    assertEquals(TrustLevel.TRUSTED, nine.level) // ≈ 0.73
    assertEquals(TrustLevel.VERY_TRUSTED, ten.level) // ≈ 0.76
  }

  @Test
  fun allFalse_isLow() {
    val trust = TrustScore.INITIAL.afterAll(Verdict.FALSE, times = 3)

    assertEquals(INITIAL_SCORE / 8, trust.score, EPS)
    assertEquals(TrustLevel.LOW, trust.level)
  }

  @Test
  fun mixed_oneEarlyFalse_keepsUserLowAfterTwoConfirmed() {
    val trust =
        TrustScore.INITIAL.after(Verdict.CONFIRMED)
            .after(Verdict.FALSE)
            .after(Verdict.CONFIRMED)
            .after(Verdict.CONFIRMED)

    // 0.3 → 0.37 → 0.185 → 0.2665 → 0.33985
    assertEquals(0.33985, trust.score, EPS)
    assertEquals(4, trust.judged)
    assertEquals(TrustLevel.LOW, trust.level)
  }

  @Test
  fun oneFalse_dropsAVeteranFromVeryTrusted_andTakesEightConfirmedToRecover() {
    val veteran = TrustScore.INITIAL.afterAll(Verdict.CONFIRMED, times = 20) // ≈ 0.91
    val afterFalse = veteran.after(Verdict.FALSE) // ≈ 0.46
    val afterSeven = afterFalse.afterAll(Verdict.CONFIRMED, times = 7) // ≈ 0.74
    val afterEight = afterSeven.after(Verdict.CONFIRMED) // ≈ 0.77

    assertEquals(TrustLevel.VERY_TRUSTED, veteran.level)
    assertEquals(TrustLevel.TRUSTED, afterFalse.level)
    assertEquals(TrustLevel.TRUSTED, afterSeven.level)
    assertEquals(TrustLevel.VERY_TRUSTED, afterEight.level)
  }

  @Test
  fun twoFalse_dropAVeteranToLow() {
    val trust =
        TrustScore.INITIAL.afterAll(Verdict.CONFIRMED, times = 20)
            .afterAll(Verdict.FALSE, times = 2) // ≈ 0.23

    assertEquals(TrustLevel.LOW, trust.level)
  }

  // ===================== Penalties and restores =====================

  @Test
  fun falseVerdict_countsAFalseSightingAndAPenalty_confirmedDoesNot() {
    val start = TrustScore(0.5, 4)

    val afterFalse = start.after(Verdict.FALSE)
    val afterConfirmed = start.after(Verdict.CONFIRMED)

    assertEquals(1, afterFalse.falseSightings)
    assertEquals(1, afterFalse.penaltiesApplied)
    assertEquals(4, afterFalse.confirmed)
    assertEquals(0, afterConfirmed.falseSightings)
    assertEquals(0, afterConfirmed.penaltiesApplied)
    assertEquals(5, afterConfirmed.confirmed)
  }

  @Test
  fun withoutPenalty_rightAfterIt_givesBackTheTrustBefore() {
    val before = TrustScore(0.6, 5)

    val restored = before.after(Verdict.FALSE).withoutPenalty(before)

    assertEquals(0.6, restored.score, EPS)
    assertEquals(5, restored.judged)
    assertEquals(0, restored.falseSightings)
    // The cancelled penalty is still counted as applied
    assertEquals(1, restored.penaltiesApplied)
  }

  @Test
  fun withoutPenalty_afterAConfirmation_isTheScoreWithoutTheFalseVerdict() {
    // 0.60, halved to 0.30, then confirmed: 0.37. Without the false verdict it would be 0.64
    val before = TrustScore(0.6, 5)
    val now = before.after(Verdict.FALSE).after(Verdict.CONFIRMED)

    val restored = now.withoutPenalty(before)

    assertEquals(0.37, now.score, EPS)
    assertEquals(0.64, restored.score, EPS)
    assertEquals(before.after(Verdict.CONFIRMED).score, restored.score, EPS)
    assertEquals(6, restored.judged)
  }

  @Test
  fun withoutPenalty_matchesTheHistoryReplayedWithoutIt() {
    val random = Random(SEED)
    repeat(200) {
      val history = List(random.nextInt(1, 15)) { randomVerdict(random) }
      val penaltyAt = random.nextInt(0, history.size + 1)
      val beforePenalty = replay(history.take(penaltyAt))
      val now = replay(history.take(penaltyAt) + Verdict.FALSE + history.drop(penaltyAt))

      val restored = now.withoutPenalty(beforePenalty)

      assertEquals(replay(history).score, restored.score, EPS)
      assertEquals(replay(history).judged, restored.judged)
      assertEquals(replay(history).falseSightings, restored.falseSightings)
    }
  }

  // Exact only if no other penalty was cancelled in between: otherwise, a bit lower, never higher

  @Test
  fun withoutPenalty_twoPenaltiesRestoredInEitherOrder_isNeverAboveTheExactScore() {
    val random = Random(SEED)
    repeat(200) {
      val history = List(random.nextInt(0, 10)) { randomVerdict(random) }
      val oldAt = random.nextInt(0, history.size + 1)
      val newAt = random.nextInt(oldAt, history.size + 1)
      val withOld = history.take(oldAt) + Verdict.FALSE + history.drop(oldAt)
      val beforeOld = replay(history.take(oldAt))
      // The new penalty comes after the old one, so after its index in withOld
      val beforeNew = replay(withOld.take(newAt + 1))
      val now = replay(withOld.take(newAt + 1) + Verdict.FALSE + withOld.drop(newAt + 1))

      val oldestFirst = now.withoutPenalty(beforeOld).withoutPenalty(beforeNew)
      val newestFirst = now.withoutPenalty(beforeNew).withoutPenalty(beforeOld)

      val exact = replay(history)
      for (restored in listOf(oldestFirst, newestFirst)) {
        assertTrue("${restored.score} > ${exact.score}", restored.score <= exact.score + EPS)
        assertEquals(exact.judged, restored.judged)
        assertEquals(exact.falseSightings, restored.falseSightings)
      }
    }
  }

  @Test(expected = IllegalArgumentException::class)
  fun withoutPenalty_ofATrustWithNoFalseSighting_isRejected() {
    val trust = TrustScore(0.6, 5).after(Verdict.CONFIRMED)

    trust.withoutPenalty(TrustScore(0.6, 5))
  }

  @Test(expected = IllegalArgumentException::class)
  fun withoutPenalty_withNoPenaltySince_isRejected() {
    // A false sighting, but from before [beforePenalty]: no penalty was applied after it
    val trust = TrustScore(0.6, 5).after(Verdict.FALSE)

    trust.withoutPenalty(trust)
  }

  @Test(expected = IllegalArgumentException::class)
  fun withoutPenalty_withALaterTrust_isRejected() {
    val before = TrustScore(0.6, 5)

    before.withoutPenalty(before.after(Verdict.FALSE))
  }

  // ===================== NEW boundary =====================

  @Test
  fun justBelowMinJudged_isNew_evenWithHighScore() {
    assertEquals(TrustLevel.NEW, TrustScore(1.0, MIN_JUDGED_SIGHTINGS - 1).level)
  }

  @Test
  fun justBelowMinJudged_isNew_evenWithLowScore() {
    assertEquals(TrustLevel.NEW, TrustScore(0.0, MIN_JUDGED_SIGHTINGS - 1).level)
  }

  @Test
  fun exactlyMinJudged_isNoLongerNew() {
    assertEquals(TrustLevel.VERY_TRUSTED, TrustScore(1.0, MIN_JUDGED_SIGHTINGS).level)
  }

  // ===================== LOW / TRUSTED boundary =====================

  @Test
  fun justBelowTrustedMin_isLow() {
    assertEquals(TrustLevel.LOW, judged(TRUSTED_MIN_SCORE - STEP).level)
  }

  @Test
  fun exactlyTrustedMin_isTrusted() {
    assertEquals(TrustLevel.TRUSTED, judged(TRUSTED_MIN_SCORE).level)
  }

  @Test
  fun justAboveTrustedMin_isTrusted() {
    assertEquals(TrustLevel.TRUSTED, judged(TRUSTED_MIN_SCORE + STEP).level)
  }

  // ===================== TRUSTED / VERY_TRUSTED boundary =====================

  @Test
  fun justBelowTrustedMax_isTrusted() {
    assertEquals(TrustLevel.TRUSTED, judged(TRUSTED_MAX_SCORE - STEP).level)
  }

  @Test
  fun exactlyTrustedMax_isTrusted() {
    assertEquals(TrustLevel.TRUSTED, judged(TRUSTED_MAX_SCORE).level)
  }

  @Test
  fun justAboveTrustedMax_isVeryTrusted() {
    assertEquals(TrustLevel.VERY_TRUSTED, judged(TRUSTED_MAX_SCORE + STEP).level)
  }

  // ===================== Invalid values =====================

  @Test(expected = IllegalArgumentException::class)
  fun negativeJudged_isRejected() {
    TrustScore(0.5, -1)
  }

  @Test(expected = IllegalArgumentException::class)
  fun negativeScore_isRejected() {
    TrustScore(-0.01, 5)
  }

  @Test(expected = IllegalArgumentException::class)
  fun scoreAboveOne_isRejected() {
    TrustScore(1.01, 5)
  }

  @Test(expected = IllegalArgumentException::class)
  fun moreFalseSightingsThanJudged_isRejected() {
    TrustScore(0.5, 2, falseSightings = 3)
  }

  @Test(expected = IllegalArgumentException::class)
  fun fewerPenaltiesThanFalseSightings_isRejected() {
    TrustScore(0.5, 5, falseSightings = 2, penaltiesApplied = 1)
  }

  @Test
  fun scoreOfExactlyZeroOrOne_isAccepted() {
    assertEquals(0.0, TrustScore(0.0, 5).score, EPS)
    assertEquals(1.0, TrustScore(1.0, 5).score, EPS)
  }

  private fun TrustScore.afterAll(verdict: Verdict, times: Int): TrustScore =
      (1..times).fold(this) { trust, _ -> trust.after(verdict) }

  /** The trust of a new user after the [verdicts], in order. */
  private fun replay(verdicts: List<Verdict>): TrustScore =
      verdicts.fold(TrustScore.INITIAL) { trust, verdict -> trust.after(verdict) }

  private fun randomVerdict(random: Random): Verdict =
      if (random.nextInt(4) == 0) Verdict.FALSE else Verdict.CONFIRMED

  /** A trust with [score] and enough judged sightings not to be NEW. */
  private fun judged(score: Double) = TrustScore(score, MIN_JUDGED_SIGHTINGS)

  private companion object {
    const val EPS = 1e-9
    const val STEP = 0.001
    const val SEED = 463
  }
}
