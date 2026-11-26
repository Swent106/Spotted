// Portions of this code were generated with the help of Copilot.
// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.spotted.R
import com.android.spotted.ui.theme.SpottedTheme
import com.android.spotted.ui.theme.jakarta

/** "12 min ago", "2 h ago", "yesterday", "3 days ago". A time in the future counts as now. */
internal fun timeAgo(pastMillis: Long, nowMillis: Long): String {
  val minutes = ((nowMillis - pastMillis) / 60_000).coerceAtLeast(0)
  return when {
    minutes < 60 -> "$minutes min ago"
    minutes < 24 * 60 -> "${minutes / 60} h ago"
    minutes < 48 * 60 -> "yesterday"
    else -> "${minutes / (24 * 60)} days ago"
  }
}

/** Label above the lost pets, e.g. "PLAINPALAIS, GENEVA · 3 KM AROUND YOU". */
internal fun areaLabel(
    homeArea: String,
    radiusKm: Int = HomeViewModel.DEFAULT_RADIUS_KM.toInt(),
): String {
  val radius = "$radiusKm KM AROUND YOU"
  return if (homeArea.isBlank()) radius else "${homeArea.trim().uppercase()} · $radius"
}

/**
 * "Lost near you": title with the number of cards, then the [AlertCarousel] of
 * [HomeUiState.carouselAlerts], nearest first (Figma "05f · Home · Lost pets carousel").
 */
internal fun LazyListScope.lostPetsSection(
    state: HomeUiState,
    onAlertClick: (String) -> Unit,
    onRetryClick: () -> Unit,
) {
  val lostPets = state.carouselAlerts
  val error = lostPets.errorMessage

  item(key = "alertsTitle") {
    LostPetsTitle(state.homeArea, state.alertRadiusKm, lostPets.items.size)
  }
  when {
    lostPets.isLoading && lostPets.items.isEmpty() ->
        item(key = "alertsLoader") { SectionLoader(HomeScreenTestTags.ALERTS_LOADER) }
    error != null && lostPets.items.isEmpty() ->
        item(key = "alertsError") { AlertsError(error, onRetryClick) }
    lostPets.isEmpty ->
        item(key = "alertsEmpty") {
          SectionMessage(
              HomeScreenTexts.NO_LOST_PETS,
              SpottedTheme.colors.textSecondary,
              HomeScreenTestTags.ALERTS_EMPTY,
          )
        }
  }

  if (state.isOffline) {
    item(key = "alertsOfflineBanner") { OfflineBanner(onRetryClick) }
  }

  if (lostPets.items.isNotEmpty()) {
    item(key = "alertCarousel") {
      AlertCarousel(
          lostPets.items,
          state.nowMillis,
          state.distanceTexts,
          onAlertClick,
          Modifier.padding(top = 14.dp),
      )
    }
  }
  if (error != null && lostPets.items.isNotEmpty()) {
    item(key = "alertsError") { AlertsError(error, onRetryClick) }
  }
}

@Composable
private fun OfflineBanner(onRetryClick: () -> Unit) {
  Row(
      modifier =
          Modifier.padding(top = 10.dp)
              .fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(SpottedTheme.colors.surface)
              .padding(horizontal = 12.dp, vertical = 4.dp)
              .testTag(HomeScreenTestTags.OFFLINE_BANNER),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        HomeScreenTexts.OFFLINE_ALERTS,
        style = jakarta(12, 16f, FontWeight.Medium, SpottedTheme.colors.ink),
    )
    TextButton(
        onClick = onRetryClick,
        modifier = Modifier.testTag(HomeScreenTestTags.OFFLINE_RETRY),
    ) {
      Text(HomeScreenTexts.RETRY, color = SpottedTheme.colors.blue)
    }
  }
}

@Composable
private fun AlertsError(message: String, onRetryClick: () -> Unit) {
  SectionError(
      message,
      HomeScreenTestTags.ALERTS_ERROR,
      HomeScreenTestTags.ALERTS_RETRY,
      onRetryClick,
  )
}

/** "GENEVA · 3 KM AROUND YOU" and "⚠ 2 pets missing near you". */
@Composable
private fun LostPetsTitle(homeArea: String, alertRadiusKm: Int, count: Int) {
  // Padded and aligned instead of fixed offsets, so the label, the icon and the title line up
  // (M1: "some buttons and texts are not centered")
  Column(
      verticalArrangement = Arrangement.spacedBy(4.dp),
      modifier =
          Modifier.padding(top = 16.dp)
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(SpottedTheme.colors.surface)
              .padding(horizontal = 14.dp, vertical = 10.dp),
  ) {
    Text(
        areaLabel(homeArea, alertRadiusKm),
        style =
            jakarta(
                11,
                14f,
                FontWeight.SemiBold,
                SpottedTheme.colors.textSecondary,
                spacing = 0.66f,
            ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Image(
          painterResource(R.drawable.ic_warning),
          contentDescription = null,
          modifier = Modifier.size(26.dp),
      )
      Text(
          "$count ${if (count == 1) "pet" else "pets"} missing near you",
          style = jakarta(17, 22f, FontWeight.ExtraBold, SpottedTheme.colors.ink),
          modifier = Modifier.testTag(HomeScreenTestTags.ALERTS_TITLE),
      )
    }
  }
}
