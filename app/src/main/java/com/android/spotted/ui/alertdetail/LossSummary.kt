// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.alertdetail

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.android.spotted.R
import com.android.spotted.model.alert.LossCause
import com.android.spotted.model.alert.LossEnvironment
import com.android.spotted.ui.addpets.ChipFlow
import com.android.spotted.ui.components.InfoChip

/**
 * The labels of how and where the pet got lost, one per chip, e.g. "Lost during a walk" then
 * "Suburb", as string resource ids. Only the known parts are kept; empty when the owner gave
 * neither.
 */
internal fun lossSummary(cause: LossCause, environment: LossEnvironment): List<Int> =
    listOfNotNull(cause.labelRes(), environment.labelRes())

/**
 * How and where the pet got lost, as chips under "Lost 2 h ago" (Figma 07c). Shows nothing when
 * both are unknown.
 */
@Composable
internal fun LossSummaryChips(cause: LossCause, environment: LossEnvironment) {
  val labels = lossSummary(cause, environment)
  if (labels.isEmpty()) return
  // The tag is on a wrapper so tests can count the chips inside it
  ChipFlow(Modifier.testTag(AlertDetailTestTags.LOSS_SUMMARY)) {
    labels.forEach { InfoChip(stringResource(it)) }
  }
}

/** Label of a known cause; [LossCause.UNKNOWN] has none, since it is never shown. */
@StringRes
private fun LossCause.labelRes(): Int? =
    when (this) {
      LossCause.ESCAPED_FROM_HOME -> R.string.loss_cause_escaped_from_home
      LossCause.DURING_A_WALK -> R.string.loss_cause_during_a_walk
      LossCause.SCARED_BY_NOISE -> R.string.loss_cause_scared_by_noise
      LossCause.DURING_MOVE_OR_TRIP -> R.string.loss_cause_during_move_or_trip
      LossCause.STOLEN -> R.string.loss_cause_stolen
      LossCause.UNKNOWN -> null
    }

/** Label of a known environment; [LossEnvironment.UNKNOWN] has none, since it is never shown. */
@StringRes
private fun LossEnvironment.labelRes(): Int? =
    when (this) {
      LossEnvironment.CITY_CENTRE -> R.string.loss_environment_city_centre
      LossEnvironment.SUBURB -> R.string.loss_environment_suburb
      LossEnvironment.VILLAGE_OR_RURAL -> R.string.loss_environment_village_or_rural
      LossEnvironment.FOREST_OR_MOUNTAIN -> R.string.loss_environment_forest_or_mountain
      LossEnvironment.UNKNOWN -> null
    }
