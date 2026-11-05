// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.spotted.model.alert.Alert
import com.android.spotted.ui.theme.SpottedTheme
import com.android.spotted.ui.theme.jakarta

/**
 * One banner per pet of the user that is missing, above "Your pets"; nothing when none is. Tapping
 * a banner opens its alert, where the owner follows the sightings.
 *
 * @param nowMillis current time, for "lost 2 h ago"; 0 when unknown, then the time is left out.
 */
internal fun LazyListScope.missingPetsSection(
    alerts: List<Alert>,
    nowMillis: Long,
    onMissingPetClick: (alertId: String) -> Unit,
) {
  items(alerts, key = { "missing_${it.id}" }) { alert ->
    MissingPetBanner(alert, nowMillis) { onMissingPetClick(alert.id) }
  }
}

/** "Milo is missing", when it was lost, and a way into its alert. */
@Composable
private fun MissingPetBanner(alert: Alert, nowMillis: Long, onClick: () -> Unit) {
  val shape = RoundedCornerShape(18.dp)
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier =
          Modifier.padding(top = 12.dp)
              .fillMaxWidth()
              .clip(shape)
              .background(SpottedTheme.colors.errorTint)
              .border(BorderStroke(1.dp, SpottedTheme.colors.lostRed), shape)
              .clickable(onClickLabel = "See ${alert.petName}'s alert", onClick = onClick)
              .padding(horizontal = 14.dp, vertical = 12.dp)
              .testTag(HomeScreenTestTags.missingBanner(alert.id)),
  ) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(SpottedTheme.colors.lostRed))
    Column(Modifier.weight(1f)) {
      Text(
          "${alert.petName} is missing",
          style = jakarta(15, 20f, FontWeight.SemiBold, SpottedTheme.colors.lostRed),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      Text(
          missingDetail(alert.lostAtMillis, nowMillis),
          style = jakarta(12, 16f, FontWeight.Medium, SpottedTheme.colors.textSecondary),
      )
    }
    Text("›", style = jakarta(22, 24f, FontWeight.SemiBold, SpottedTheme.colors.lostRed))
  }
}

/** "Lost 2 h ago · see the sightings", or only "See the sightings" when the time is unknown. */
internal fun missingDetail(lostAtMillis: Long, nowMillis: Long): String =
    if (nowMillis <= 0L) "See the sightings"
    else "Lost ${timeAgo(lostAtMillis, nowMillis)} · see the sightings"
