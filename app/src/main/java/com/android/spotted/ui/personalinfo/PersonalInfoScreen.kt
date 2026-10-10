package com.android.spotted.ui.personalinfo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.spotted.R
import com.android.spotted.ui.theme.PlusJakartaSans
import com.android.spotted.ui.theme.SpottedAvatarBackground
import com.android.spotted.ui.theme.SpottedBlue
import com.android.spotted.ui.theme.SpottedBlueTint
import com.android.spotted.ui.theme.SpottedError
import com.android.spotted.ui.theme.SpottedInk
import com.android.spotted.ui.theme.SpottedInkSoft
import com.android.spotted.ui.theme.SpottedInputBackground
import com.android.spotted.ui.theme.SpottedMapBackground
import com.android.spotted.ui.theme.SpottedScreenBackground
import com.android.spotted.ui.theme.SpottedTextLabel
import com.android.spotted.ui.theme.SpottedTextSecondary

/** Test tags used by UI tests to find the elements of the Personal Info screen. */
object PersonalInfoScreenTestTags {
  const val SCREEN = "personalInfo_screen"
  const val BACK_BUTTON = "personalInfo_backButton"
  const val AVATAR = "personalInfo_avatar"
  const val CHANGE_PHOTO = "personalInfo_changePhoto"
  const val USERNAME_INPUT = "personalInfo_usernameInput"
  const val FIRST_NAME_INPUT = "personalInfo_firstNameInput"
  const val LAST_NAME_INPUT = "personalInfo_lastNameInput"
  const val HOME_AREA_INPUT = "personalInfo_homeAreaInput"
  const val USE_MY_LOCATION = "personalInfo_useMyLocation"
  const val MAP_PREVIEW = "personalInfo_mapPreview"
  const val RADIUS_TAG = "personalInfo_radiusTag"
  const val ERROR_MESSAGE = "personalInfo_errorMessage"
  const val CREATE_PROFILE_BUTTON = "personalInfo_createProfileButton"
  const val LOADER = "personalInfo_loader"

  fun radiusChip(radiusKm: Int) = "personalInfo_radiusChip_$radiusKm"
}

// Figma's Bold (700) is not bundled: SemiBold is the closest available weight.
private val Bold = FontWeight.SemiBold

private fun jakarta(
    size: Int,
    line: Int,
    weight: FontWeight,
    color: Color,
    align: TextAlign = TextAlign.Start,
) =
    TextStyle(
        fontSize = size.sp,
        lineHeight = line.sp,
        fontFamily = PlusJakartaSans,
        fontWeight = weight,
        color = color,
        textAlign = align,
    )

private val labelStyle = jakarta(11, 15, Bold, SpottedTextLabel)
private val inputStyle = jakarta(14, 18, FontWeight.SemiBold, SpottedInk)
private val placeholderStyle = jakarta(14, 18, FontWeight.Medium, SpottedTextLabel)

/**
 * Stateless "Create your profile" screen.
 *
 * The form scrolls, and the "Create profile" button stays pinned at the bottom. The button is
 * enabled only when [PersonalInfoUiState.canSave] is true.
 */
@Composable
fun PersonalInfoContent(
    state: PersonalInfoUiState,
    onBackClick: () -> Unit,
    onChangePhotoClick: () -> Unit,
    onUsernameChange: (String) -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onHomeAreaChange: (String) -> Unit,
    onUseMyLocationClick: () -> Unit,
    onAlertRadiusSelect: (Int) -> Unit,
    onCreateProfileClick: () -> Unit,
) {
  Box(
      Modifier.fillMaxSize()
          .background(SpottedScreenBackground)
          .testTag(PersonalInfoScreenTestTags.SCREEN)
  ) {
    Image(
        painterResource(R.drawable.background_textures),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = 0.4f,
        modifier = Modifier.fillMaxSize(),
    )
    Column(
        Modifier.fillMaxSize().padding(start = 20.dp, top = 52.dp, end = 20.dp, bottom = 34.dp)
    ) {
      // Body
      Column(
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
      ) {
        Header(onBackClick)
        Avatar(onChangePhotoClick)
        IdentityCard(state, onUsernameChange, onFirstNameChange, onLastNameChange)
        HomeLocationCard(state, onHomeAreaChange, onUseMyLocationClick, onAlertRadiusSelect)
        state.errorMessage?.let { message ->
          Text(
              message,
              Modifier.fillMaxWidth().testTag(PersonalInfoScreenTestTags.ERROR_MESSAGE),
              style = jakarta(13, 18, FontWeight.SemiBold, SpottedError, TextAlign.Center),
          )
        }
      }
      Spacer(Modifier.height(12.dp))
      CreateProfileButton(state, onCreateProfileClick)
    }
  }
}

@Composable
private fun Header(onBackClick: () -> Unit) {
  Row(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth(),
  ) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .clickable(onClickLabel = "Back", role = Role.Button, onClick = onBackClick)
                .testTag(PersonalInfoScreenTestTags.BACK_BUTTON),
    ) {
      Text("<", style = jakarta(18, 22, Bold, SpottedInk))
    }
    Column(Modifier.weight(1f)) {
      Text("Create your profile", style = jakarta(23, 31, FontWeight.ExtraBold, SpottedInk))
      Text(
          "Step 2 of 2 · tell the community who you are",
          style = jakarta(12, 16, FontWeight.Medium, SpottedTextSecondary),
      )
    }
  }
}

@Composable
private fun Avatar(onChangePhotoClick: () -> Unit) {
  Column(
      verticalArrangement = Arrangement.spacedBy(8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.fillMaxWidth(),
  ) {
    Box(
        Modifier.size(96.dp)
            .clickable(onClickLabel = "Change photo", onClick = onChangePhotoClick)
            .testTag(PersonalInfoScreenTestTags.AVATAR)
    ) {
      // Empty photo slot: the profile photo is user data, picked later through the Photo Picker.
      Box(
          Modifier.size(96.dp)
              .clip(CircleShape)
              .background(SpottedAvatarBackground)
              .border(4.dp, Color.White, CircleShape)
      )
      Box(
          contentAlignment = Alignment.Center,
          modifier =
              Modifier.offset(68.dp, 66.dp)
                  .size(30.dp)
                  .clip(CircleShape)
                  .background(SpottedBlue)
                  .border(3.dp, Color.White, CircleShape),
      ) {
        Text("+", style = jakarta(16, 18, FontWeight.ExtraBold, Color.White))
      }
    }
    Text(
        "Change photo (optional)",
        style = jakarta(12, 16, Bold, SpottedBlue),
        modifier =
            Modifier.clickable(role = Role.Button, onClick = onChangePhotoClick)
                .testTag(PersonalInfoScreenTestTags.CHANGE_PHOTO),
    )
  }
}

@Composable
private fun IdentityCard(
    state: PersonalInfoUiState,
    onUsernameChange: (String) -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
) {
  Card {
    Text("USERNAME", style = labelStyle)
    InputField(
        value = state.username,
        onValueChange = onUsernameChange,
        placeholder = "@username",
        testTag = PersonalInfoScreenTestTags.USERNAME_INPUT,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
        Text("FIRST NAME", style = labelStyle)
        InputField(
            value = state.firstName,
            onValueChange = onFirstNameChange,
            placeholder = "First name",
            testTag = PersonalInfoScreenTestTags.FIRST_NAME_INPUT,
            capitalization = KeyboardCapitalization.Words,
        )
      }
      Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
        Text("LAST NAME", style = labelStyle)
        InputField(
            value = state.lastName,
            onValueChange = onLastNameChange,
            placeholder = "Last name",
            testTag = PersonalInfoScreenTestTags.LAST_NAME_INPUT,
            capitalization = KeyboardCapitalization.Words,
        )
      }
    }
  }
}

@Composable
private fun HomeLocationCard(
    state: PersonalInfoUiState,
    onHomeAreaChange: (String) -> Unit,
    onUseMyLocationClick: () -> Unit,
    onAlertRadiusSelect: (Int) -> Unit,
) {
  Card {
    Text("HOME LOCATION", style = labelStyle)
    InputField(
        value = state.homeArea,
        onValueChange = onHomeAreaChange,
        placeholder = "Neighbourhood, city",
        testTag = PersonalInfoScreenTestTags.HOME_AREA_INPUT,
        capitalization = KeyboardCapitalization.Words,
        imeAction = ImeAction.Done,
    ) {
      Text(
          "Use my location",
          style = jakarta(12, 16, Bold, SpottedBlue),
          modifier =
              Modifier.padding(start = 8.dp)
                  .clickable(role = Role.Button, onClick = onUseMyLocationClick)
                  .testTag(PersonalInfoScreenTestTags.USE_MY_LOCATION),
      )
    }
    Text(
        "Used to alert you about pets missing near your home.",
        style = jakarta(12, 16, FontWeight.Medium, SpottedTextSecondary),
    )
    MapPreview(state.alertRadiusKm)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
      PersonalInfoUiState.ALERT_RADIUS_OPTIONS_KM.forEach { radiusKm ->
        RadiusChip(radiusKm, selected = radiusKm == state.alertRadiusKm, onAlertRadiusSelect)
      }
    }
  }
}

/** Static preview of the alert area. The circles stay centred whatever the card width. */
@Composable
private fun MapPreview(alertRadiusKm: Int) {
  Box(
      Modifier.fillMaxWidth()
          .height(118.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(SpottedMapBackground)
          .testTag(PersonalInfoScreenTestTags.MAP_PREVIEW)
  ) {
    Image(
        painterResource(R.drawable.personal_info_map),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            Modifier.wrapContentSize(Alignment.TopCenter, unbounded = true)
                .offset(y = (-106).dp)
                .size(330.dp),
    )
    Image(
        painterResource(R.drawable.personal_info_alert_radius),
        contentDescription = null,
        modifier = Modifier.align(Alignment.TopCenter).offset(y = 7.dp),
    )
    Image(
        painterResource(R.drawable.personal_info_home_pin),
        contentDescription = null,
        modifier = Modifier.align(Alignment.TopCenter).offset(y = 50.dp),
    )
    Text(
        "Alerts within $alertRadiusKm km",
        style = jakarta(11, 14, Bold, SpottedInk),
        modifier =
            Modifier.offset(10.dp, 86.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(Color.White.copy(alpha = 0.95f))
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .testTag(PersonalInfoScreenTestTags.RADIUS_TAG),
    )
  }
}

@Composable
private fun RowScope.RadiusChip(radiusKm: Int, selected: Boolean, onSelect: (Int) -> Unit) {
  val shape = RoundedCornerShape(100.dp)
  Box(
      contentAlignment = Alignment.Center,
      modifier =
          Modifier.weight(1f)
              .clip(shape)
              .background(if (selected) SpottedBlueTint else SpottedInputBackground)
              .then(if (selected) Modifier.border(1.5.dp, SpottedBlue, shape) else Modifier)
              .selectable(selected = selected, role = Role.RadioButton) { onSelect(radiusKm) }
              .padding(vertical = 7.dp)
              .testTag(PersonalInfoScreenTestTags.radiusChip(radiusKm)),
  ) {
    Text(
        "$radiusKm km",
        style =
            if (selected) jakarta(13, 16, Bold, SpottedBlue)
            else jakarta(13, 16, FontWeight.SemiBold, SpottedInkSoft),
    )
  }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
  Column(
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(22.dp))
              .background(Color.White)
              .padding(16.dp),
  ) {
    content()
  }
}

@Composable
private fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    testTag: String,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Next,
    trailing: @Composable (() -> Unit)? = null,
) {
  BasicTextField(
      value = value,
      onValueChange = onValueChange,
      singleLine = true,
      textStyle = inputStyle,
      keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = imeAction),
      modifier = Modifier.fillMaxWidth().testTag(testTag),
      decorationBox = { innerTextField ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SpottedInputBackground)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
          Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = placeholderStyle)
            innerTextField()
          }
          trailing?.invoke()
        }
      },
  )
}

@Composable
private fun CreateProfileButton(state: PersonalInfoUiState, onClick: () -> Unit) {
  Button(
      onClick = onClick,
      enabled = state.canSave,
      shape = RoundedCornerShape(14.dp),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = SpottedBlue,
              contentColor = Color.White,
              disabledContainerColor = SpottedBlue.copy(alpha = 0.4f),
              disabledContentColor = Color.White,
          ),
      modifier =
          Modifier.fillMaxWidth()
              .height(52.dp)
              .testTag(PersonalInfoScreenTestTags.CREATE_PROFILE_BUTTON),
  ) {
    if (state.isSaving) {
      CircularProgressIndicator(
          color = Color.White,
          strokeWidth = 2.dp,
          modifier = Modifier.size(20.dp).testTag(PersonalInfoScreenTestTags.LOADER),
      )
    } else {
      Text("Create profile", style = jakarta(15, 20, Bold, Color.White))
    }
  }
}

/**
 * Stateful entry point of the Personal Info screen.
 *
 * Collects the [PersonalInfoViewModel] state, forwards user input to it, and calls [onSaved] once
 * the profile is saved. Photo picking and device location are launched by the caller through
 * [onChangePhotoClick] and [onUseMyLocationClick].
 */
@Composable
fun PersonalInfoScreen(
    viewModel: PersonalInfoViewModel = viewModel(factory = PersonalInfoViewModel.Factory),
    onBack: () -> Unit = {},
    onSaved: () -> Unit = {},
    onChangePhotoClick: () -> Unit = {},
    onUseMyLocationClick: () -> Unit = {},
) {
  val state by viewModel.uiState.collectAsState()

  val currentOnSaved by rememberUpdatedState(onSaved)
  LaunchedEffect(state.isSaved) { if (state.isSaved) currentOnSaved() }

  PersonalInfoContent(
      state = state,
      onBackClick = onBack,
      onChangePhotoClick = onChangePhotoClick,
      onUsernameChange = viewModel::onUsernameChanged,
      onFirstNameChange = viewModel::onFirstNameChanged,
      onLastNameChange = viewModel::onLastNameChanged,
      onHomeAreaChange = viewModel::onHomeAreaChanged,
      onUseMyLocationClick = onUseMyLocationClick,
      onAlertRadiusSelect = viewModel::onAlertRadiusChanged,
      onCreateProfileClick = viewModel::saveProfile,
  )
}
