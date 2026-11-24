// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.vote

import com.android.spotted.model.trust.TrustScore
import com.android.spotted.model.vote.VoteRules.THRESHOLD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoteRulesTest {

  @Test
  fun weightAfter_addsTheVotersScore() {
    assertEquals(0.5, VoteRules.weightAfter(0.0, voter(0.5)), EPS)
    assertEquals(1.3, VoteRules.weightAfter(0.5, voter(0.8)), EPS)
  }

  @Test
  fun justBelowTheThreshold_doesNotHide() {
    assertFalse(VoteRules.reachesThreshold(THRESHOLD - STEP))
  }

  @Test
  fun atTheThreshold_hides() {
    assertTrue(VoteRules.reachesThreshold(THRESHOLD))
  }

  @Test
  fun aboveTheThreshold_hides() {
    assertTrue(VoteRules.reachesThreshold(THRESHOLD + STEP))
  }

  @Test
  fun mixedWeights_hideOnlyOnceTheirTotalReachesTheThreshold() {
    val afterTwo =
        listOf(0.9, 0.45).fold(0.0) { total, score -> VoteRules.weightAfter(total, voter(score)) }
    val afterThree = VoteRules.weightAfter(afterTwo, voter(0.7))

    assertFalse(VoteRules.reachesThreshold(afterTwo))
    assertTrue(VoteRules.reachesThreshold(afterThree))
  }

  @Test
  fun twoVeryTrustedVoters_areNotEnough() {
    val total = VoteRules.weightAfter(VoteRules.weightAfter(0.0, voter(0.99)), voter(0.99))

    assertFalse(VoteRules.reachesThreshold(total))
  }

  @Test
  fun fiveVotersAtTheLowestTrustedScore_areEnough() {
    val total = (1..5).fold(0.0) { weight, _ -> VoteRules.weightAfter(weight, voter(0.4)) }

    assertTrue(VoteRules.reachesThreshold(total))
  }

  private fun voter(score: Double) = TrustScore(score, judged = 5)

  private companion object {
    const val EPS = 1e-9
    const val STEP = 0.001
  }
}
