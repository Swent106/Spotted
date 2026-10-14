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
internal fun areaLabel(homeArea: String): String {
  val radius = "${HomeViewModel.DEFAULT_RADIUS_KM.toInt()} KM AROUND YOU"
  return if (homeArea.isBlank()) radius else "${homeArea.trim().uppercase()} · $radius"
}

/** "Lost near you": title with the count, then the alerts, newest first. */
@Composable
internal fun LostPetsSection(
    state: HomeUiState,
    onAlertClick: (String) -> Unit,
    onSeenPetClick: (String) -> Unit,
) {
  val lostPets = state.lostPetsNearby
  val count = lostPets.items.size

  Spacer(Modifier.height(16.dp))
  Box(
      Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)
  ) {
    Text(
        areaLabel(state.homeArea),
        style = jakarta(11, 14f, FontWeight.SemiBold, SpottedTextSecondary, spacing = 0.66f),
        modifier = Modifier.offset(9.dp, 4.dp),
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

  when {
    lostPets.isLoading && lostPets.items.isEmpty() ->
        SectionLoader(HomeScreenTestTags.ALERTS_LOADER)
    lostPets.errorMessage != null && lostPets.items.isEmpty() ->
        SectionMessage(lostPets.errorMessage, SpottedLostRed, HomeScreenTestTags.ALERTS_ERROR)
    lostPets.isEmpty ->
        SectionMessage(
            HomeScreenTexts.NO_LOST_PETS,
            SpottedTextSecondary,
            HomeScreenTestTags.ALERTS_EMPTY,
        )
  }

  // The newest alert is highlighted, like Milo in Figma
  lostPets.items.forEachIndexed { index, alert ->
    Spacer(Modifier.height(if (index == 0) 10.dp else 16.dp))
    AlertCard(alert, featured = index == 0, state.nowMillis, onAlertClick, onSeenPetClick)
  }
  if (lostPets.errorMessage != null && lostPets.items.isNotEmpty()) {
    SectionMessage(lostPets.errorMessage, SpottedLostRed, HomeScreenTestTags.ALERTS_ERROR)
  }
}

/** One lost pet: photo, name, species, time lost; the featured one has "I have seen this pet". */
@Composable
private fun AlertCard(
    alert: Alert,
    featured: Boolean,
    nowMillis: Long,
    onAlertClick: (String) -> Unit,
    onSeenPetClick: (String) -> Unit,
) {
  Column(
      verticalArrangement = Arrangement.spacedBy(14.dp),
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(22.dp))
              .background(Color.White)
              .clickable { onAlertClick(alert.id) }
              .padding(16.dp)
              .testTag(HomeScreenTestTags.alertCard(alert.id)),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      PetPhoto(
          alert.petPhotoUrl,
          Modifier.size(if (featured) 78.dp else 58.dp)
              .clip(RoundedCornerShape(if (featured) 18.dp else 15.dp))
              .background(if (featured) SpottedFeaturedPhotoBackground else SpottedAvatarBackground),
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
      }
    }
    if (featured) {
      Button(
          onClick = { onSeenPetClick(alert.id) },
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = SpottedBlue),
          modifier =
              Modifier.fillMaxWidth().height(50.dp).testTag(HomeScreenTestTags.seenButton(alert.id)),
      ) {
        Text("I have seen this pet", style = jakarta(15, 20f, FontWeight.SemiBold, Color.White))
      }
    }
  }
}
