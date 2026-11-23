// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.alertdetail

import com.android.spotted.R
import com.android.spotted.model.alert.LossCause
import com.android.spotted.model.alert.LossEnvironment
import org.junit.Assert.assertEquals
import org.junit.Test

class LossSummaryTest {

  @Test
  fun bothKnown_giveTwoChips_causeFirst() {
    assertEquals(
        listOf(R.string.loss_cause_during_a_walk, R.string.loss_environment_suburb),
        lossSummary(LossCause.DURING_A_WALK, LossEnvironment.SUBURB),
    )
  }

  @Test
  fun onlyOneKnown_givesOnlyThatChip() {
    assertEquals(
        listOf(R.string.loss_cause_stolen),
        lossSummary(LossCause.STOLEN, LossEnvironment.UNKNOWN),
    )
    assertEquals(
        listOf(R.string.loss_environment_forest_or_mountain),
        lossSummary(LossCause.UNKNOWN, LossEnvironment.FOREST_OR_MOUNTAIN),
    )
  }

  @Test
  fun noneKnown_givesNoChip() {
    assertEquals(emptyList<Int>(), lossSummary(LossCause.UNKNOWN, LossEnvironment.UNKNOWN))
  }

  @Test
  fun everyKnownValue_hasItsOwnLabel() {
    val causes =
        LossCause.entries
            .filter { it != LossCause.UNKNOWN }
            .map { lossSummary(it, LossEnvironment.UNKNOWN).single() }
    val environments =
        LossEnvironment.entries
            .filter { it != LossEnvironment.UNKNOWN }
            .map { lossSummary(LossCause.UNKNOWN, it).single() }

    // No two values share a label
    assertEquals(causes.size + environments.size, (causes + environments).toSet().size)
  }
}
