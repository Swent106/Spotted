// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.trust

import com.android.spotted.model.trust.TrustRules.GAIN_RATE
import com.android.spotted.model.trust.TrustRules.INITIAL_SCORE
import com.android.spotted.model.trust.TrustRules.LOSS_FACTOR
import com.android.spotted.model.trust.TrustRules.MIN_JUDGED_SIGHTINGS
import com.android.spotted.model.trust.TrustRules.TRUSTED_MAX_SCORE
import com.android.spotted.model.trust.TrustRules.TRUSTED_MIN_SCORE
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

  @Test
  fun scoreOfExactlyZeroOrOne_isAccepted() {
    assertEquals(0.0, TrustScore(0.0, 5).score, EPS)
    assertEquals(1.0, TrustScore(1.0, 5).score, EPS)
  }

  private fun TrustScore.afterAll(verdict: Verdict, times: Int): TrustScore =
      (1..times).fold(this) { trust, _ -> trust.after(verdict) }

  /** A trust with [score] and enough judged sightings not to be NEW. */
  private fun judged(score: Double) = TrustScore(score, MIN_JUDGED_SIGHTINGS)

  private companion object {
    const val EPS = 1e-9
    const val STEP = 0.001
  }
}
