package com.android.spotted.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.android.spotted.R
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.Species
import com.android.spotted.ui.theme.PlusJakartaSans
import com.android.spotted.ui.theme.SpottedAvatarBackground
import com.android.spotted.ui.theme.SpottedHomeBackground
import com.android.spotted.ui.theme.SpottedInk
import com.android.spotted.ui.theme.SpottedLostRed
import com.android.spotted.ui.theme.SpottedReportBorder
import com.android.spotted.ui.theme.SpottedTabBarBorder
import com.android.spotted.ui.theme.SpottedTabBorder
import com.android.spotted.ui.theme.SpottedTabSelected
import com.android.spotted.ui.theme.SpottedTextSecondary

/** Test tags of the Home screen (`<feature>_<element>`). */
object HomeScreenTestTags {
  const val SCREEN = "home_screen"
  const val BODY = "home_body"
  const val SETTINGS_BUTTON = "home_settingsButton"
  const val NOTIFICATIONS_BUTTON = "home_notificationsButton"
  const val ERROR_MESSAGE = "home_errorMessage"
  const val PETS_LOADER = "home_petsLoader"
  const val PETS_ERROR = "home_petsError"
  const val PETS_EMPTY = "home_petsEmpty"
  const val REPORT_MISSING_BUTTON = "home_reportMissingButton"
  const val TAB_BAR = "home_tabBar"

  fun petCard(petId: String) = "home_pet_$petId"
}

/** Texts shown when a section has nothing to show. */
object HomeScreenTexts {
  const val NO_PETS = "No pets yet. Add one so people know how to care for them."
}

/** Text style of the Figma designs. Figma's Bold (700) uses SemiBold, the bundled weight. */
internal fun jakarta(
    size: Int,
    line: Float,
    weight: FontWeight,
    color: Color,
    spacing: Float = 0f,
) =
    TextStyle(
        fontSize = size.sp,
        lineHeight = line.sp,
        fontFamily = PlusJakartaSans,
        fontWeight = weight,
        color = color,
        letterSpacing = spacing.sp,
    )

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
  )
}

/** Stateless Home ("Near you") screen, from the Figma frame "01 · Near you". */
@Composable
fun HomeScreenContent(
    state: HomeUiState,
    onReportMissingClick: () -> Unit = {},
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
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 91.dp)
            .testTag(HomeScreenTestTags.BODY)
    ) {
      Header(state.userName, onSettingsClick, onNotificationsClick)

      state.errorMessage?.let { message ->
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = jakarta(13, 17f, FontWeight.SemiBold, SpottedLostRed),
            modifier = Modifier.testTag(HomeScreenTestTags.ERROR_MESSAGE),
        )
      }

      PetsSection(state.pets)

      // Report a pet missing
      Spacer(Modifier.height(9.dp))
      OutlinedButton(
          onClick = onReportMissingClick,
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, SpottedReportBorder),
          colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
          modifier =
              Modifier.fillMaxWidth().height(52.dp).testTag(HomeScreenTestTags.REPORT_MISSING_BUTTON),
      ) {
        Text("Report a pet missing", style = jakarta(15, 20f, FontWeight.SemiBold, SpottedLostRed))
      }
    }

    TabBar(
        onMapClick = onMapClick,
        onCameraClick = onCameraClick,
        onProfileClick = onProfileClick,
        modifier = Modifier.align(Alignment.BottomCenter),
    )
  }
}

/** "Hey Rayen", settings and notifications. */
@Composable
private fun Header(userName: String, onSettingsClick: () -> Unit, onNotificationsClick: () -> Unit) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().height(58.dp),
  ) {
    // Avatar: empty until the profile has a photo
    Box(Modifier.size(58.dp).clip(RoundedCornerShape(27.dp)).background(SpottedAvatarBackground))
    Spacer(Modifier.width(9.dp))
    Text(
        if (userName.isBlank()) "Hey" else "Hey $userName",
        style = jakarta(19, 17f, FontWeight.SemiBold, Color.Black),
        modifier = Modifier.weight(1f),
    )
    HeaderButton(
        R.drawable.ic_settings,
        "Settings",
        18.759.dp,
        onSettingsClick,
        HomeScreenTestTags.SETTINGS_BUTTON,
    )
    Spacer(Modifier.width(8.dp))
    HeaderButton(
        R.drawable.ic_notifications,
        "Notifications",
        20.966.dp,
        onNotificationsClick,
        HomeScreenTestTags.NOTIFICATIONS_BUTTON,
    )
  }
}

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
          Modifier.size(32.dp)
              .clip(RoundedCornerShape(14.897.dp))
              .background(Color.White)
              .clickable { onClick() }
              .testTag(tag),
  ) {
    Image(painterResource(icon), contentDescription = description, modifier = Modifier.size(iconSize))
  }
}

/** "Your pets": one card per pet, with its own loading, error and empty state. */
@Composable
private fun PetsSection(pets: HomeSection<Pet>) {
  when {
    pets.isLoading && pets.items.isEmpty() -> SectionLoader(HomeScreenTestTags.PETS_LOADER)
    pets.errorMessage != null && pets.items.isEmpty() ->
        SectionMessage(pets.errorMessage, SpottedLostRed, HomeScreenTestTags.PETS_ERROR)
    pets.isEmpty ->
        SectionMessage(HomeScreenTexts.NO_PETS, SpottedTextSecondary, HomeScreenTestTags.PETS_EMPTY)
  }
  pets.items.forEach { pet ->
    Spacer(Modifier.height(18.dp))
    Box(
        Modifier.fillMaxWidth()
            .height(131.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .testTag(HomeScreenTestTags.petCard(pet.id))
    ) {
      PetPhoto(pet.photoUrl, Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(218.dp))
      Column(Modifier.padding(start = 13.dp, top = 14.dp).width(116.dp)) {
        Text("Your pets:", style = jakarta(15, 20f, FontWeight.SemiBold, SpottedInk))
        Spacer(Modifier.height(11.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(pet.name, style = jakarta(15, 20f, FontWeight.SemiBold, SpottedInk))
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
        )
      }
    }
  }
  // A refresh that failed keeps the pets and shows the error under them
  if (pets.errorMessage != null && pets.items.isNotEmpty()) {
    SectionMessage(pets.errorMessage, SpottedLostRed, HomeScreenTestTags.PETS_ERROR)
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

/** Home, Map, Camera, Profile. Home is selected. */
@Composable
private fun TabBar(
    onMapClick: () -> Unit,
    onCameraClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
          modifier
              .padding(bottom = 7.dp)
              .offset(x = 2.dp)
              .width(264.dp)
              .height(56.dp)
              .clip(RoundedCornerShape(28.dp))
              .background(Color.White)
              .border(1.dp, SpottedTabBarBorder, RoundedCornerShape(28.dp))
              .padding(horizontal = 3.dp)
              .testTag(HomeScreenTestTags.TAB_BAR),
  ) {
    TabButton(R.drawable.ic_tab_home, 26.dp, 81.dp, "Home", selected = true, onClick = {})
    Spacer(Modifier.width(6.dp))
    TabButton(R.drawable.ic_tab_map, 31.dp, 53.dp, "Map", selected = false, onClick = onMapClick)
    Spacer(Modifier.width(7.dp))
    TabButton(R.drawable.ic_tab_camera, 26.dp, 52.dp, "Camera", false, onCameraClick)
    Spacer(Modifier.width(7.dp))
    TabButton(R.drawable.ic_tab_profile, 33.dp, 51.dp, "Profile", false, onProfileClick)
  }
}

@Composable
private fun TabButton(
    icon: Int,
    iconSize: Dp,
    width: Dp,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
  Box(
      contentAlignment = Alignment.Center,
      modifier =
          Modifier.width(width)
              .height(51.dp)
              .clip(RoundedCornerShape(27.dp))
              .background(if (selected) SpottedTabSelected else Color.White)
              .border(1.dp, SpottedTabBorder, RoundedCornerShape(27.dp))
              .clickable { onClick() },
  ) {
    Image(painterResource(icon), contentDescription = description, modifier = Modifier.size(iconSize))
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
