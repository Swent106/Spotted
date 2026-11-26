// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * Horizontal carousel of lost pets, from the Figma frame "05f · Home · Lost pets carousel". Tapping
 * a card calls [onAlertClick] with its alert id.
 *
 * @param alerts the cards, in the order to show them.
 * @param nowMillis time used for "Lost 2 h ago".
 * @param distanceTexts "600 m away" of each alert, by alert id; a card without one shows no
 *   distance.
 */
@Composable
fun AlertCarousel(
    alerts: List<Alert>,
    nowMillis: Long,
    distanceTexts: Map<String, String>,
    onAlertClick: (alertId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
  LazyRow(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier = modifier.fillMaxWidth().testTag(HomeScreenTestTags.ALERT_CAROUSEL),
  ) {
    items(alerts, key = { it.id }) { alert ->
      AlertCarouselCard(alert, nowMillis, distanceTexts[alert.id], onAlertClick)
    }
  }
}

/** One card: photo, name, species, time lost, and the distance when known. */
@Composable
private fun AlertCarouselCard(
    alert: Alert,
    nowMillis: Long,
    distanceText: String?,
    onAlertClick: (alertId: String) -> Unit,
) {
  Column(
      verticalArrangement = Arrangement.spacedBy(14.dp),
      modifier =
          Modifier.size(width = 220.dp, height = 265.dp)
              .clip(RoundedCornerShape(22.dp))
              .background(SpottedTheme.colors.surface)
              .clickable(onClickLabel = "See ${alert.petName}'s alert") { onAlertClick(alert.id) }
              .padding(16.dp)
              .testTag(HomeScreenTestTags.alertCard(alert.id)),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      PetPhoto(
          alert.petPhotoUrl,
          Modifier.fillMaxWidth()
              .height(112.dp)
              .clip(RoundedCornerShape(18.dp))
              .background(SpottedTheme.colors.featuredPhotoBackground),
      )
      Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            alert.petName,
            style = jakarta(20, 26f, FontWeight.Bold, SpottedTheme.colors.ink),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            alert.petSpecies.name.lowercase().replaceFirstChar { it.uppercase() },
            style = jakarta(14, 18f, FontWeight.Medium, SpottedTheme.colors.textSecondary),
        )
        Text(
            "Lost ${timeAgo(alert.lostAtMillis, nowMillis)}",
            style = jakarta(13, 17f, FontWeight.SemiBold, SpottedTheme.colors.lostRed),
        )
      }
    }
    if (distanceText != null) {
      Text(
          distanceText,
          style = jakarta(12, 16f, FontWeight.SemiBold, SpottedTheme.colors.inkSoft),
          modifier =
              Modifier.clip(RoundedCornerShape(100.dp))
                  .background(SpottedTheme.colors.inputBackground)
                  .padding(horizontal = 11.dp, vertical = 7.dp)
                  .testTag(HomeScreenTestTags.alertDistance(alert.id)),
      )
    }
  }
}
