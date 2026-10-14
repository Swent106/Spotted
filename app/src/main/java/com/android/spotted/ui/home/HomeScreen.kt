package com.android.spotted.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.android.spotted.R
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.Species
import com.android.spotted.ui.theme.SpottedAvatarBackground
import com.android.spotted.ui.theme.SpottedError
import com.android.spotted.ui.theme.SpottedHomeBackground
import com.android.spotted.ui.theme.SpottedInk
import com.android.spotted.ui.theme.SpottedLostRed
import com.android.spotted.ui.theme.SpottedReportBorder
import com.android.spotted.ui.theme.SpottedTabBarBorder
import com.android.spotted.ui.theme.SpottedTabBorder
import com.android.spotted.ui.theme.SpottedTabSelected
import com.android.spotted.ui.theme.SpottedTextSecondary
import com.android.spotted.ui.theme.jakarta

/** Test tags of the Home screen (`<feature>_<element>`). */
object HomeScreenTestTags {
  const val SCREEN = "home_screen"
  const val BODY = "home_body"
  const val SETTINGS_BUTTON = "home_settingsButton"
  const val NOTIFICATIONS_BUTTON = "home_notificationsButton"
  const val ERROR_MESSAGE = "home_errorMessage"
  const val PETS_TITLE = "home_petsTitle"
  const val PETS_LOADER = "home_petsLoader"
  const val PETS_ERROR = "home_petsError"
  const val PETS_RETRY = "home_petsRetry"
  const val PETS_EMPTY = "home_petsEmpty"
  const val REPORT_MISSING_BUTTON = "home_reportMissingButton"
  const val TAB_BAR = "home_tabBar"
  const val TAB_HOME = "home_tabHome"
  const val TAB_MAP = "home_tabMap"
  const val TAB_CAMERA = "home_tabCamera"
  const val TAB_PROFILE = "home_tabProfile"

  fun petCard(petId: String) = "home_pet_$petId"
}

/** Texts of the Home screen that tests check. */
object HomeScreenTexts {
  const val PETS_TITLE = "Your pets"
  const val NO_PETS = "No pets yet. Add one so people know how to care for them."
  const val RETRY = "Retry"
}

/** Space left under the scrolling content so that the tab bar never hides it. */
private val TAB_BAR_SPACE = 91.dp

/**
 * Home screen wired to its [HomeViewModel]. Navigation is done by the caller through the callbacks.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
    onReportMissingClick: () -> Unit = {},
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  HomeScreenContent(
      state = state,
      onReportMissingClick = onReportMissingClick,
      onRetryClick = viewModel::refresh,
  )
}

/** Stateless Home ("Near you") screen, from the Figma frame "01 · Near you". */
@Composable
fun HomeScreenContent(
    state: HomeUiState,
    onReportMissingClick: () -> Unit = {},
    onRetryClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onMapClick: () -> Unit = {},
    onCameraClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
) {
  Box(Modifier.fillMaxSize().background(SpottedHomeBackground).testTag(HomeScreenTestTags.SCREEN)) {
    // Background texture
    Image(
        painterResource(R.drawable.background_textures),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize().alpha(0.4f),
    )

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = TAB_BAR_SPACE)
            .testTag(HomeScreenTestTags.BODY)
    ) {
      Header(state.userName, onSettingsClick, onNotificationsClick)

      state.errorMessage?.let { message ->
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = jakarta(13, 17f, FontWeight.SemiBold, SpottedError),
            modifier = Modifier.testTag(HomeScreenTestTags.ERROR_MESSAGE),
        )
      }

      PetsSection(state.pets, onRetryClick)

      // Report a pet missing: at the end of the scroll, as in Figma
      Spacer(Modifier.height(9.dp))
      OutlinedButton(
          onClick = onReportMissingClick,
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, SpottedReportBorder),
          colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
          modifier =
              Modifier.fillMaxWidth()
                  .height(52.dp)
                  .testTag(HomeScreenTestTags.REPORT_MISSING_BUTTON),
      ) {
        Text("Report a pet missing", style = jakarta(15, 20f, FontWeight.SemiBold, SpottedLostRed))
      }
    }

    TabBar(
        onClicks = listOf({}, onMapClick, onCameraClick, onProfileClick),
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
    )
  }
}

/** "Hey Rayen", settings and notifications. */
@Composable
private fun Header(
    userName: String,
    onSettingsClick: () -> Unit,
    onNotificationsClick: () -> Unit,
) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    // Avatar: empty until the profile has a photo
    Box(Modifier.size(58.dp).clip(RoundedCornerShape(27.dp)).background(SpottedAvatarBackground))
    Spacer(Modifier.width(9.dp))
    Text(
        if (userName.isBlank()) "Hey" else "Hey $userName",
        style = jakarta(19, 24f, FontWeight.SemiBold, Color.Black),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
    HeaderButton(
        R.drawable.ic_settings,
        "Settings",
        19.dp,
        onSettingsClick,
        HomeScreenTestTags.SETTINGS_BUTTON,
    )
    HeaderButton(
        R.drawable.ic_notifications,
        "Notifications",
        21.dp,
        onNotificationsClick,
        HomeScreenTestTags.NOTIFICATIONS_BUTTON,
    )
  }
}

/** A 32 dp white circle as in Figma, inside a 48 dp touch target. */
@Composable
private fun HeaderButton(
    icon: Int,
    description: String,
    iconSize: Dp,
    onClick: () -> Unit,
    tag: String,
) {
  Box(
      contentAlignment = Alignment.Center,
      modifier =
          Modifier.size(48.dp)
              .clip(RoundedCornerShape(24.dp))
              .clickable(onClick = onClick)
              .testTag(tag),
  ) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(Color.White),
    ) {
      Image(painterResource(icon), description, Modifier.size(iconSize))
    }
  }
}

/** "Your pets": one title, then one card per pet, with its own loading, error and empty state. */
@Composable
private fun PetsSection(pets: HomeSection<Pet>, onRetryClick: () -> Unit) {
  Spacer(Modifier.height(18.dp))
  Text(
      HomeScreenTexts.PETS_TITLE,
      style = jakarta(15, 20f, FontWeight.SemiBold, SpottedInk),
      modifier = Modifier.testTag(HomeScreenTestTags.PETS_TITLE),
  )
  when {
    pets.isLoading && pets.items.isEmpty() -> SectionLoader(HomeScreenTestTags.PETS_LOADER)
    pets.errorMessage != null && pets.items.isEmpty() -> PetsError(pets.errorMessage, onRetryClick)
    pets.isEmpty ->
        SectionMessage(HomeScreenTexts.NO_PETS, SpottedTextSecondary, HomeScreenTestTags.PETS_EMPTY)
  }
  pets.items.forEach { pet ->
    Spacer(Modifier.height(12.dp))
    PetCard(pet)
  }
  // A refresh that failed keeps the pets and shows the error under them
  if (pets.errorMessage != null && pets.items.isNotEmpty()) {
    PetsError(pets.errorMessage, onRetryClick)
  }
}

@Composable
private fun PetsError(message: String, onRetryClick: () -> Unit) {
  SectionMessage(message, SpottedError, HomeScreenTestTags.PETS_ERROR)
  TextButton(onClick = onRetryClick, modifier = Modifier.testTag(HomeScreenTestTags.PETS_RETRY)) {
    Text(HomeScreenTexts.RETRY, style = jakarta(14, 18f, FontWeight.SemiBold, SpottedTabSelected))
  }
}

/** The pet's name and status on the left, its photo on the right; grows with long texts. */
@Composable
private fun PetCard(pet: Pet) {
  Row(
      Modifier.fillMaxWidth()
          .heightIn(min = 131.dp)
          .height(IntrinsicSize.Min)
          .clip(RoundedCornerShape(22.dp))
          .background(Color.White)
          .testTag(HomeScreenTestTags.petCard(pet.id))
  ) {
    Column(Modifier.weight(1f).padding(start = 13.dp, top = 14.dp, end = 8.dp, bottom = 14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            pet.name,
            style = jakarta(15, 20f, FontWeight.SemiBold, SpottedInk),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(3.dp))
        Image(
            painterResource(R.drawable.ic_check_verified),
            contentDescription = "Profile ready",
            modifier = Modifier.size(21.dp),
        )
      }
      Spacer(Modifier.height(3.dp))
      Text(
          "Profile ready if they go missing",
          style = jakarta(13, 17f, FontWeight.Medium, SpottedTextSecondary),
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
      )
    }
    PetPhoto(pet.photoUrl, Modifier.weight(1.6f).fillMaxHeight())
  }
}

@Composable
internal fun SectionLoader(tag: String) {
  Spacer(Modifier.height(16.dp))
  Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(Modifier.testTag(tag))
  }
}

@Composable
internal fun SectionMessage(message: String, color: Color, tag: String) {
  Spacer(Modifier.height(12.dp))
  Text(
      message,
      style = jakarta(14, 18f, FontWeight.SemiBold, color),
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(Color.White)
              .padding(16.dp)
              .testTag(tag),
  )
}

/** A tab of the tab bar, sized as in Figma. */
private class Tab(
    val icon: Int,
    val iconSize: Dp,
    val width: Dp,
    val label: String,
    val tag: String,
)

private val TABS =
    listOf(
        Tab(R.drawable.ic_tab_home, 26.dp, 81.dp, "Home", HomeScreenTestTags.TAB_HOME),
        Tab(R.drawable.ic_tab_map, 31.dp, 53.dp, "Map", HomeScreenTestTags.TAB_MAP),
        Tab(R.drawable.ic_tab_camera, 26.dp, 52.dp, "Camera", HomeScreenTestTags.TAB_CAMERA),
        Tab(R.drawable.ic_tab_profile, 33.dp, 51.dp, "Profile", HomeScreenTestTags.TAB_PROFILE),
    )

/**
 * The [TABS], with one click callback per tab in the same order. Home is selected, and each tab
 * tells accessibility services whether it is the selected one.
 */
@Composable
private fun TabBar(onClicks: List<() -> Unit>, modifier: Modifier = Modifier) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(7.dp),
      modifier =
          modifier
              .padding(bottom = 7.dp)
              .width(264.dp)
              .height(56.dp)
              .clip(RoundedCornerShape(28.dp))
              .background(Color.White)
              .border(1.dp, SpottedTabBarBorder, RoundedCornerShape(28.dp))
              .padding(horizontal = 3.dp)
              .testTag(HomeScreenTestTags.TAB_BAR),
  ) {
    TABS.forEachIndexed { index, tab ->
      val selected = index == 0
      Box(
          contentAlignment = Alignment.Center,
          modifier =
              Modifier.width(tab.width)
                  .height(51.dp)
                  .clip(RoundedCornerShape(27.dp))
                  .background(if (selected) SpottedTabSelected else Color.White)
                  .border(1.dp, SpottedTabBorder, RoundedCornerShape(27.dp))
                  .selectable(selected = selected, role = Role.Tab, onClick = onClicks[index])
                  .testTag(tab.tag),
      ) {
        Image(painterResource(tab.icon), tab.label, Modifier.size(tab.iconSize))
      }
    }
  }
}

/** Photo loaded from its URL with Coil. Null shows only the background. */
@Composable
internal fun PetPhoto(url: String?, modifier: Modifier) {
  Box(modifier) {
    if (url != null) {
      AsyncImage(
          model = url,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.matchParentSize(),
      )
    }
  }
}

// ------------------------------------------ Preview ------------------------------------------

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenContentPreview() {
  HomeScreenContent(
      state =
          HomeUiState(
              userName = "Rayen",
              pets =
                  HomeSection(
                      listOf(Pet(id = "luna", ownerId = "me", name = "Luna", species = Species.DOG))
                  ),
          )
  )
}
