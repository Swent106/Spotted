// Portions of this code were generated with the help of Copilot.
// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.android.spotted.model.alert.Alert
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

/** "Lost near you": title with the count, then the alerts, newest first. */
internal fun LazyListScope.lostPetsSection(
    state: HomeUiState,
    onAlertClick: (String) -> Unit,
    onSeenPetClick: (String) -> Unit,
    onRetryClick: () -> Unit,
) {
  val lostPets = state.lostPetsNearby
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

  // The first alert is the newest (HomeViewModel sorts them): it is highlighted, like Milo in Figma
  itemsIndexed(lostPets.items, key = { _, alert -> "alert_${alert.id}" }) { index, alert ->
    AlertCard(
        alert,
        featured = index == 0,
        state.nowMillis,
        state.distanceTexts[alert.id],
        onAlertClick,
        onSeenPetClick,
        Modifier.padding(top = if (index == 0) 10.dp else 16.dp),
    )
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

/**
 * One lost pet: photo, name, species, time lost, distance when known; the featured one has "I have
 * seen this pet".
 */
@Composable
private fun AlertCard(
    alert: Alert,
    featured: Boolean,
    nowMillis: Long,
    distanceText: String?,
    onAlertClick: (String) -> Unit,
    onSeenPetClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      verticalArrangement = Arrangement.spacedBy(14.dp),
      modifier =
          modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(22.dp))
              .background(SpottedTheme.colors.surface)
              .clickable(onClickLabel = "See ${alert.petName}'s alert") { onAlertClick(alert.id) }
              .padding(16.dp)
              .testTag(HomeScreenTestTags.alertCard(alert.id)),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      PetPhoto(
          alert.petPhotoUrl,
          Modifier.size(if (featured) 78.dp else 58.dp)
              .clip(RoundedCornerShape(if (featured) 18.dp else 15.dp))
              .background(
                  if (featured) SpottedTheme.colors.featuredPhotoBackground
                  else SpottedTheme.colors.avatarBackground
              ),
      )
      Spacer(Modifier.width(14.dp))
      Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
          Text(
              alert.petName,
              style =
                  if (featured) jakarta(20, 26f, FontWeight.SemiBold, SpottedTheme.colors.ink)
                  else jakarta(17, 22f, FontWeight.SemiBold, SpottedTheme.colors.ink),
          )
          if (featured) {
            Spacer(Modifier.width(9.dp))
            Text(
                "More info",
                style = jakarta(14, 18f, FontWeight.Medium, SpottedTheme.colors.textSecondary),
            )
          }
        }
        Text(
            alert.petSpecies.name.lowercase().replaceFirstChar { it.uppercase() },
            style = jakarta(14, 18f, FontWeight.Medium, SpottedTheme.colors.textSecondary),
        )
        Text(
            "Lost ${timeAgo(alert.lostAtMillis, nowMillis)}",
            style =
                jakarta(
                    13,
                    17f,
                    FontWeight.SemiBold,
                    if (featured) SpottedTheme.colors.lostRed
                    else SpottedTheme.colors.textSecondary,
                ),
        )
        if (distanceText != null) {
          Text(
              distanceText,
              style = jakarta(13, 17f, FontWeight.Medium, SpottedTheme.colors.textSecondary),
              modifier = Modifier.testTag(HomeScreenTestTags.alertDistance(alert.id)),
          )
        }
      }
    }
    if (featured) {
      Button(
          onClick = { onSeenPetClick(alert.id) },
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = SpottedTheme.colors.blue),
          modifier =
              Modifier.fillMaxWidth()
                  .height(50.dp)
                  .testTag(HomeScreenTestTags.seenButton(alert.id)),
      ) {
        Text(
            "I have seen this pet",
            style = jakarta(15, 20f, FontWeight.SemiBold, SpottedTheme.colors.onAccent),
        )
      }
    }
  }
}
