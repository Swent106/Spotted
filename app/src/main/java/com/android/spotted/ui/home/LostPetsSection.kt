package com.android.spotted.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.spotted.R
import com.android.spotted.model.alert.Alert
import com.android.spotted.ui.theme.SpottedAvatarBackground
import com.android.spotted.ui.theme.SpottedBlue
import com.android.spotted.ui.theme.SpottedFeaturedPhotoBackground
import com.android.spotted.ui.theme.SpottedInk
import com.android.spotted.ui.theme.SpottedLostRed
import com.android.spotted.ui.theme.SpottedTextSecondary
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
              SpottedTextSecondary,
              HomeScreenTestTags.ALERTS_EMPTY,
          )
        }
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
  Box(
      Modifier.padding(top = 16.dp)
          .fillMaxWidth()
          .height(58.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(Color.White)
  ) {
    Text(
        areaLabel(homeArea, alertRadiusKm),
        style = jakarta(11, 14f, FontWeight.SemiBold, SpottedTextSecondary, spacing = 0.66f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.offset(9.dp, 4.dp).padding(end = 18.dp),
    )
    Image(
        painterResource(R.drawable.ic_warning),
        contentDescription = null,
        modifier = Modifier.offset(9.dp, 14.dp).size(33.dp),
    )
    Text(
        "$count ${if (count == 1) "pet" else "pets"} missing near you",
        style = jakarta(17, 34f, FontWeight.ExtraBold, SpottedInk),
        modifier = Modifier.offset(47.dp, 16.dp).testTag(HomeScreenTestTags.ALERTS_TITLE),
    )
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
              .background(Color.White)
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
                  if (featured) SpottedFeaturedPhotoBackground else SpottedAvatarBackground
              ),
      )
      Spacer(Modifier.width(14.dp))
      Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
          Text(
              alert.petName,
              style =
                  if (featured) jakarta(20, 26f, FontWeight.SemiBold, SpottedInk)
                  else jakarta(17, 22f, FontWeight.SemiBold, SpottedInk),
          )
          if (featured) {
            Spacer(Modifier.width(9.dp))
            Text("More info", style = jakarta(14, 18f, FontWeight.Medium, SpottedTextSecondary))
          }
        }
        Text(
            alert.petSpecies.name.lowercase().replaceFirstChar { it.uppercase() },
            style = jakarta(14, 18f, FontWeight.Medium, SpottedTextSecondary),
        )
        Text(
            "Lost ${timeAgo(alert.lostAtMillis, nowMillis)}",
            style =
                jakarta(
                    13,
                    17f,
                    FontWeight.SemiBold,
                    if (featured) SpottedLostRed else SpottedTextSecondary,
                ),
        )
        if (distanceText != null) {
          Text(
              distanceText,
              style = jakarta(13, 17f, FontWeight.Medium, SpottedTextSecondary),
              modifier = Modifier.testTag(HomeScreenTestTags.alertDistance(alert.id)),
          )
        }
      }
    }
    if (featured) {
      Button(
          onClick = { onSeenPetClick(alert.id) },
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = SpottedBlue),
          modifier =
              Modifier.fillMaxWidth()
                  .height(50.dp)
                  .testTag(HomeScreenTestTags.seenButton(alert.id)),
      ) {
        Text("I have seen this pet", style = jakarta(15, 20f, FontWeight.SemiBold, Color.White))
      }
    }
  }
}
