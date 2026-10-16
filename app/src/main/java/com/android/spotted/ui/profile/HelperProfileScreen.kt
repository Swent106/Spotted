package com.android.spotted.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.spotted.R
import com.android.spotted.ui.home.TabBar
import com.android.spotted.ui.home.TabIndex
import com.android.spotted.ui.theme.SpottedBadgeEarned
import com.android.spotted.ui.theme.SpottedBadgeLocked
import com.android.spotted.ui.theme.SpottedBadgeLockedIcon
import com.android.spotted.ui.theme.SpottedBlue
import com.android.spotted.ui.theme.SpottedError
import com.android.spotted.ui.theme.SpottedInk
import com.android.spotted.ui.theme.SpottedProfileAvatar
import com.android.spotted.ui.theme.SpottedProfileBackground
import com.android.spotted.ui.theme.SpottedProfileInitial
import com.android.spotted.ui.theme.SpottedRankYou
import com.android.spotted.ui.theme.SpottedStatText
import com.android.spotted.ui.theme.SpottedTextFaint
import com.android.spotted.ui.theme.SpottedTextLabel
import com.android.spotted.ui.theme.SpottedTextMuted
import com.android.spotted.ui.theme.SpottedTextSecondary
import com.android.spotted.ui.theme.jakarta

/** Test tags used by UI tests to find the elements of the [HelperProfileContent]. */
object HelperProfileTestTags {
  const val SCREEN = "helperProfile_screen"
  const val NAME = "helperProfile_name"
  const val AREA = "helperProfile_area"
  const val PETS_BROUGHT_HOME = "helperProfile_petsBroughtHome"
  const val EDIT_PROFILE = "helperProfile_editProfile"
  const val ERROR = "helperProfile_error"

  fun badge(name: String) = "helperProfile_badge_$name"
}

/** Where the Helper profile can send the user: the other tabs and the profile form. */
data class HelperProfileNavigation(
    val onHomeClick: () -> Unit = {},
    val onMapClick: () -> Unit = {},
    val onCameraClick: () -> Unit = {},
    val onEditProfileClick: () -> Unit = {},
)

/** Helper profile (Figma "13 · Helper profile"), wired to its [HelperProfileViewModel]. */
@Composable
fun HelperProfileScreen(
    viewModel: HelperProfileViewModel = viewModel(factory = HelperProfileViewModel.Factory),
    navigation: HelperProfileNavigation = HelperProfileNavigation(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  // Runs again when coming back from the profile form, so the changes are shown
  LaunchedEffect(Unit) { viewModel.load() }

  HelperProfileContent(state = state, navigation = navigation)
}

/** Stateless Helper profile, the part UI tests target. */
@Composable
fun HelperProfileContent(
    state: HelperProfileUiState,
    navigation: HelperProfileNavigation = HelperProfileNavigation(),
) {
  Box(
      Modifier.fillMaxSize()
          .background(SpottedProfileBackground)
          .testTag(HelperProfileTestTags.SCREEN)
  ) {
    Image(
        painterResource(R.drawable.background_textures),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize().alpha(0.4f),
    )
    Column(Modifier.fillMaxSize()) {
      Column(
          verticalArrangement = Arrangement.spacedBy(14.dp),
          modifier =
              Modifier.weight(1f)
                  .verticalScroll(rememberScrollState())
                  .statusBarsPadding()
                  .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
      ) {
        Header(state)
        state.errorMessage?.let { message ->
          Text(
              message,
              style = jakarta(13, 17f, FontWeight.SemiBold, SpottedError),
              modifier = Modifier.testTag(HelperProfileTestTags.ERROR),
          )
        }
        PetsBroughtHome(state.petsBroughtHome)
        SectionLabel("BADGES")
        Badges(state)
        SectionLabel("HELPERS AROUND YOU THIS YEAR")
        Leaderboard(state.petsBroughtHome)
        EditProfile(state, navigation.onEditProfileClick)
      }
      Box(Modifier.fillMaxWidth().navigationBarsPadding(), Alignment.Center) {
        TabBar(
            onClicks =
                listOf(navigation.onHomeClick, navigation.onMapClick, navigation.onCameraClick, {}),
            selectedIndex = TabIndex.PROFILE,
        )
      }
    }
  }
}

/** Avatar, name and area on a white card. */
@Composable
private fun Header(state: HelperProfileUiState) {
  Box(Modifier.fillMaxWidth()) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.align(Alignment.CenterStart)
                .clip(RoundedCornerShape(19.dp))
                .background(Color.White)
                .padding(end = 16.dp),
    ) {
      Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier.size(56.dp).clip(CircleShape).background(SpottedProfileAvatar),
      ) {
        Text(state.initial, style = jakarta(22, 29f, FontWeight.ExtraBold, SpottedProfileInitial))
      }
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            state.name,
            style = jakarta(22, 29f, FontWeight.ExtraBold, SpottedInk),
            maxLines = 1,
            modifier = Modifier.testTag(HelperProfileTestTags.NAME),
        )
        Text(
            if (state.homeArea.isBlank()) "Helping out nearby"
            else "Helping out in ${state.homeArea}",
            style = jakarta(10, 17f, FontWeight.Medium, SpottedTextSecondary),
            maxLines = 1,
            modifier = Modifier.testTag(HelperProfileTestTags.AREA),
        )
      }
    }
  }
}

/** Blue card: how many pets the user helped bring home. */
@Composable
private fun PetsBroughtHome(count: Int) {
  Column(
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(22.dp))
              .background(SpottedBlue)
              .padding(18.dp),
  ) {
    Text(
        if (count == 1) "1 pet brought home" else "$count pets brought home",
        style = jakarta(25, 33f, FontWeight.ExtraBold, Color.White),
        modifier = Modifier.testTag(HelperProfileTestTags.PETS_BROUGHT_HOME),
    )
    Text(
        "Thanks to sightings you reported and their owners confirmed",
        style = jakarta(13, 17f, FontWeight.Medium, SpottedStatText),
    )
  }
}

@Composable
private fun SectionLabel(text: String) {
  Text(text, style = jakarta(11, 14f, FontWeight.SemiBold, SpottedTextLabel, spacing = 0.66f))
}

@Composable
private fun Badges(state: HelperProfileUiState) {
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
    Badge("First find", R.drawable.ic_badge_first_find, earned = state.hasFirstFind)
    Badge("5 confirmed", R.drawable.ic_badge_confirmed, earned = state.hasFiveConfirmed)
    // Not tracked yet: sightings don't record the time of day
    Badge("Night walker", R.drawable.ic_badge_night_walker, earned = false)
  }
}

/** A badge; not earned yet, it is greyed out. */
@Composable
private fun RowScope.Badge(label: String, icon: Int, earned: Boolean) {
  Column(
      verticalArrangement = Arrangement.spacedBy(8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier =
          Modifier.weight(1f)
              .clip(RoundedCornerShape(18.dp))
              .background(Color.White)
              .padding(vertical = 14.dp)
              .testTag(HelperProfileTestTags.badge(label)),
  ) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.size(44.dp)
                .clip(CircleShape)
                .background(if (earned) SpottedBadgeEarned else SpottedBadgeLocked),
    ) {
      Image(
          painterResource(icon),
          contentDescription = null,
          colorFilter = if (earned) null else ColorFilter.tint(SpottedBadgeLockedIcon),
          modifier = Modifier.size(22.dp),
      )
    }
    Text(
        label,
        style = jakarta(12, 16f, FontWeight.SemiBold, if (earned) SpottedInk else SpottedTextMuted),
    )
  }
}

/** Other helpers are not ranked yet, so only the user's own count is listed. */
@Composable
private fun Leaderboard(petsBroughtHome: Int) {
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
              .background(Color.White)
              .padding(horizontal = 16.dp, vertical = 6.dp),
  ) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
    ) {
      Text("1", style = jakarta(13, 17f, FontWeight.ExtraBold, SpottedRankYou))
      Text(
          "You",
          style = jakarta(15, 20f, FontWeight.ExtraBold, SpottedInk),
          modifier = Modifier.weight(1f),
      )
      Text(
          if (petsBroughtHome == 1) "1 pet" else "$petsBroughtHome pets",
          style = jakarta(13, 17f, FontWeight.SemiBold, SpottedTextSecondary),
      )
    }
  }
}

/** Opens the profile form, to update the name, home area and alert radius. */
@Composable
private fun EditProfile(state: HelperProfileUiState, onClick: () -> Unit) {
  Row(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
              .background(Color.White)
              .clickable(onClickLabel = "Edit your profile", role = Role.Button, onClick = onClick)
              .padding(horizontal = 16.dp, vertical = 14.dp)
              .testTag(HelperProfileTestTags.EDIT_PROFILE),
  ) {
    Image(
        painterResource(R.drawable.ic_tab_profile),
        contentDescription = null,
        modifier = Modifier.size(22.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
      Text("Edit your profile", style = jakarta(15, 20f, FontWeight.SemiBold, SpottedInk))
      Text(
          if (state.homeArea.isBlank()) "Name, home area and alerts"
          else "${state.homeArea} · alerts within ${state.alertRadiusKm} km",
          style = jakarta(12, 16f, FontWeight.Medium, SpottedTextSecondary),
          maxLines = 1,
      )
    }
    Text(">", style = jakarta(16, 21f, FontWeight.SemiBold, SpottedTextFaint))
  }
}
