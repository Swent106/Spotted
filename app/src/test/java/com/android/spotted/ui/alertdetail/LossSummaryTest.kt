// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.alertdetail

import com.android.spotted.R
import com.android.spotted.model.alert.LossCause
import com.android.spotted.model.alert.LossEnvironment
import org.junit.Assert.assertEquals
import org.junit.Test

class LossSummaryTest {

  @Test
  fun bothKnown_givesBothLabels_causeFirst() {
    assertEquals(
        listOf(R.string.loss_cause_during_a_walk, R.string.loss_environment_suburb),
        lossSummary(LossCause.DURING_A_WALK, LossEnvironment.SUBURB),
    )
  }

  @Test
  fun onlyOneKnown_givesOnlyThatLabel() {
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
  fun noneKnown_givesNoLabel() {
    assertEquals(emptyList<Int>(), lossSummary(LossCause.UNKNOWN, LossEnvironment.UNKNOWN))
  }

  @Test
  fun everyCause_hasItsExpectedLabel() {
    val expected =
        mapOf(
            LossCause.ESCAPED_FROM_HOME to listOf(R.string.loss_cause_escaped_from_home),
            LossCause.DURING_A_WALK to listOf(R.string.loss_cause_during_a_walk),
            LossCause.SCARED_BY_NOISE to listOf(R.string.loss_cause_scared_by_noise),
            LossCause.DURING_MOVE_OR_TRIP to listOf(R.string.loss_cause_during_move_or_trip),
            LossCause.STOLEN to listOf(R.string.loss_cause_stolen),
            LossCause.UNKNOWN to emptyList(),
        )

    // Every value is in the table, so a new cause can't be added without its label
    assertEquals(LossCause.entries.toSet(), expected.keys)
    expected.forEach { (cause, labels) ->
      assertEquals("$cause", labels, lossSummary(cause, LossEnvironment.UNKNOWN))
    }
  }

  @Test
  fun everyEnvironment_hasItsExpectedLabel() {
    val expected =
        mapOf(
            LossEnvironment.CITY_CENTRE to listOf(R.string.loss_environment_city_centre),
            LossEnvironment.SUBURB to listOf(R.string.loss_environment_suburb),
            LossEnvironment.VILLAGE_OR_RURAL to listOf(R.string.loss_environment_village_or_rural),
            LossEnvironment.FOREST_OR_MOUNTAIN to
                listOf(R.string.loss_environment_forest_or_mountain),
            LossEnvironment.UNKNOWN to emptyList(),
        )

    assertEquals(LossEnvironment.entries.toSet(), expected.keys)
    expected.forEach { (environment, labels) ->
      assertEquals("$environment", labels, lossSummary(LossCause.UNKNOWN, environment))
    }
  }
}
