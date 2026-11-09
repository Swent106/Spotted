// Portions of this code were generated with the help of Claude (Anthropic).
// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.personalinfo

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.imageLoader
import com.android.spotted.R
import com.android.spotted.model.Location
import com.android.spotted.ui.components.CameraPhotoFile
import com.android.spotted.ui.components.MapPicker
import com.android.spotted.ui.components.SpottedBackButton
import com.android.spotted.ui.components.rememberPhotoPickerActions
import com.android.spotted.ui.theme.PlusJakartaSans
import com.android.spotted.ui.theme.SpottedTheme

/** Test tags used by UI tests to find the elements of the Personal Info screen. */
object PersonalInfoScreenTestTags {
  const val SCREEN = "personalInfo_screen"
  const val BACK_BUTTON = "personalInfo_backButton"
  const val AVATAR = "personalInfo_avatar"
  const val CHANGE_PHOTO = "personalInfo_changePhoto"
  const val PHOTO = "personalInfo_photo"
  const val PHOTO_OPTIONS = "personalInfo_photoOptions"
  const val PHOTO_FROM_GALLERY = "personalInfo_photoFromGallery"
  const val PHOTO_FROM_CAMERA = "personalInfo_photoFromCamera"
  const val PHOTO_REMOVE = "personalInfo_photoRemove"
  const val USERNAME_INPUT = "personalInfo_usernameInput"
  const val FIRST_NAME_INPUT = "personalInfo_firstNameInput"
  const val LAST_NAME_INPUT = "personalInfo_lastNameInput"
  const val HOME_AREA_INPUT = "personalInfo_homeAreaInput"
  const val USE_MY_LOCATION = "personalInfo_useMyLocation"
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

// Getters, so they follow the light or dark palette of the current theme
private val labelStyle: TextStyle
  @Composable get() = jakarta(11, 15, Bold, SpottedTheme.colors.textLabel)
private val inputStyle: TextStyle
  @Composable get() = jakarta(14, 18, FontWeight.SemiBold, SpottedTheme.colors.ink)
private val placeholderStyle: TextStyle
  @Composable get() = jakarta(14, 18, FontWeight.Medium, SpottedTheme.colors.textLabel)

// Height of the home map, from the Figma design.
private val MapPickerHeight = 118.dp

/**
 * Stateless "Create your profile" screen.
 *
 * The form scrolls, and the "Create profile" button stays pinned at the bottom. The button is
 * enabled only when [PersonalInfoUiState.canSave] is true. Back is shown only when [onBackClick] is
 * given: during onboarding there is no screen to go back to.
 */
@Composable
fun PersonalInfoContent(
    state: PersonalInfoUiState,
    onBackClick: (() -> Unit)?,
    onChangePhotoClick: () -> Unit,
    onUsernameChange: (String) -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onHomeAreaChange: (String) -> Unit,
    onUseMyLocationClick: () -> Unit,
    onHomeLocationChange: (Location) -> Unit,
    onAlertRadiusSelect: (Int) -> Unit,
    onCreateProfileClick: () -> Unit,
    isPickingPhoto: Boolean = false,
) {
  Box(
      Modifier.fillMaxSize()
          .background(SpottedTheme.colors.screenBackground)
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
        Header(state.isEditing, onBackClick)
        Avatar(state.photoUri, onChangePhotoClick, !state.isSaving && !isPickingPhoto)
        IdentityCard(state, onUsernameChange, onFirstNameChange, onLastNameChange)
        HomeLocationCard(
            state,
            onHomeAreaChange,
            onUseMyLocationClick,
            onHomeLocationChange,
            onAlertRadiusSelect,
        )
        state.errorMessage?.let { message ->
          Text(
              message,
              Modifier.fillMaxWidth().testTag(PersonalInfoScreenTestTags.ERROR_MESSAGE),
              style =
                  jakarta(13, 18, FontWeight.SemiBold, SpottedTheme.colors.error, TextAlign.Center),
          )
        }
      }
      Spacer(Modifier.height(12.dp))
      CreateProfileButton(state, onCreateProfileClick)
    }
  }
}

@Composable
private fun Header(isEditing: Boolean, onBackClick: (() -> Unit)?) {
  Row(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth(),
  ) {
    if (onBackClick != null) {
      SpottedBackButton(onClick = onBackClick, testTag = PersonalInfoScreenTestTags.BACK_BUTTON)
    }
    Column(Modifier.weight(1f)) {
      Text(
          stringResource(
              if (isEditing) R.string.personal_info_title_edit
              else R.string.personal_info_title_create
          ),
          style = jakarta(23, 31, FontWeight.ExtraBold, SpottedTheme.colors.ink),
      )
      Text(
          stringResource(
              if (isEditing) R.string.personal_info_edit_subtitle
              else R.string.personal_info_onboarding_step
          ),
          style = jakarta(12, 16, FontWeight.Medium, SpottedTheme.colors.textSecondary),
      )
    }
  }
}

@Composable
private fun Avatar(photoUri: String?, onChangePhotoClick: () -> Unit, enabled: Boolean) {
  Column(
      verticalArrangement = Arrangement.spacedBy(8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.fillMaxWidth(),
  ) {
    Box(
        Modifier.size(96.dp)
            .clickable(
                enabled = enabled,
                onClickLabel = stringResource(R.string.photo_change),
                onClick = onChangePhotoClick,
            )
            .testTag(PersonalInfoScreenTestTags.AVATAR)
    ) {
      Box(
          Modifier.size(96.dp)
              .clip(CircleShape)
              .background(SpottedTheme.colors.avatarBackground)
              .border(4.dp, SpottedTheme.colors.surface, CircleShape)
      ) {
        if (photoUri != null) {
          AsyncImage(
              model = photoUri,
              contentDescription = stringResource(R.string.profile_photo_description),
              contentScale = ContentScale.Crop,
              modifier =
                  Modifier.matchParentSize()
                      .padding(4.dp)
                      .clip(CircleShape)
                      .testTag(PersonalInfoScreenTestTags.PHOTO),
          )
        }
      }
      Box(
          contentAlignment = Alignment.Center,
          modifier =
              Modifier.offset(68.dp, 66.dp)
                  .size(30.dp)
                  .clip(CircleShape)
                  .background(SpottedTheme.colors.blue)
                  .border(3.dp, SpottedTheme.colors.surface, CircleShape),
      ) {
        Text("+", style = jakarta(16, 18, FontWeight.ExtraBold, SpottedTheme.colors.onAccent))
      }
    }
    Text(
        stringResource(R.string.profile_photo_change_optional),
        style = jakarta(12, 16, Bold, SpottedTheme.colors.blue),
        modifier =
            Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onChangePhotoClick)
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
    Text(stringResource(R.string.personal_info_username_section), style = labelStyle)
    InputField(
        value = state.username,
        onValueChange = onUsernameChange,
        placeholder = stringResource(R.string.personal_info_username_placeholder),
        testTag = PersonalInfoScreenTestTags.USERNAME_INPUT,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
        Text(stringResource(R.string.personal_info_first_name_section), style = labelStyle)
        InputField(
            value = state.firstName,
            onValueChange = onFirstNameChange,
            placeholder = stringResource(R.string.personal_info_first_name_placeholder),
            testTag = PersonalInfoScreenTestTags.FIRST_NAME_INPUT,
            capitalization = KeyboardCapitalization.Words,
        )
      }
      Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
        Text(stringResource(R.string.personal_info_last_name_section), style = labelStyle)
        InputField(
            value = state.lastName,
            onValueChange = onLastNameChange,
            placeholder = stringResource(R.string.personal_info_last_name_placeholder),
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
    onHomeLocationChange: (Location) -> Unit,
    onAlertRadiusSelect: (Int) -> Unit,
) {
  Card {
    Text(stringResource(R.string.personal_info_home_section), style = labelStyle)
    InputField(
        value = state.homeArea,
        onValueChange = onHomeAreaChange,
        placeholder = stringResource(R.string.personal_info_home_placeholder),
        testTag = PersonalInfoScreenTestTags.HOME_AREA_INPUT,
        capitalization = KeyboardCapitalization.Words,
        imeAction = ImeAction.Done,
    ) {
      Text(
          stringResource(R.string.personal_info_use_my_location),
          style = jakarta(12, 16, Bold, SpottedTheme.colors.blue),
          modifier =
              Modifier.padding(start = 8.dp)
                  .clickable(role = Role.Button, onClick = onUseMyLocationClick)
                  .testTag(PersonalInfoScreenTestTags.USE_MY_LOCATION),
      )
    }
    Text(
        stringResource(R.string.personal_info_home_explanation),
        style = jakarta(12, 16, FontWeight.Medium, SpottedTheme.colors.textSecondary),
    )
    // The label is built outside composition, so the format is read here
    val alertsWithinKm = stringResource(R.string.personal_info_alerts_within_km)
    MapPicker(
        location = state.homeLocation,
        radiusKm = state.alertRadiusKm,
        onLocationChange = onHomeLocationChange,
        modifier = Modifier.fillMaxWidth().height(MapPickerHeight),
        hint = stringResource(R.string.personal_info_map_hint),
        radiusLabel = { alertsWithinKm.format(it) },
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().selectableGroup(),
    ) {
      PersonalInfoUiState.ALERT_RADIUS_OPTIONS_KM.forEach { radiusKm ->
        RadiusChip(radiusKm, selected = radiusKm == state.alertRadiusKm, onAlertRadiusSelect)
      }
    }
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
              .background(
                  if (selected) SpottedTheme.colors.blueTint
                  else SpottedTheme.colors.inputBackground
              )
              .then(
                  if (selected) Modifier.border(1.5.dp, SpottedTheme.colors.blue, shape)
                  else Modifier
              )
              .selectable(selected = selected, role = Role.RadioButton) { onSelect(radiusKm) }
              .padding(vertical = 7.dp)
              .testTag(PersonalInfoScreenTestTags.radiusChip(radiusKm)),
  ) {
    Text(
        stringResource(R.string.personal_info_radius_km, radiusKm),
        style =
            if (selected) jakarta(13, 16, Bold, SpottedTheme.colors.blue)
            else jakarta(13, 16, FontWeight.SemiBold, SpottedTheme.colors.inkSoft),
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
              .background(SpottedTheme.colors.surface)
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
                    .background(SpottedTheme.colors.inputBackground)
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
              containerColor = SpottedTheme.colors.blue,
              contentColor = SpottedTheme.colors.onAccent,
              disabledContainerColor = SpottedTheme.colors.blue.copy(alpha = 0.4f),
              disabledContentColor = SpottedTheme.colors.onAccent,
          ),
      modifier =
          Modifier.fillMaxWidth()
              .height(52.dp)
              .testTag(PersonalInfoScreenTestTags.CREATE_PROFILE_BUTTON),
  ) {
    if (state.isSaving) {
      CircularProgressIndicator(
          color = SpottedTheme.colors.onAccent,
          strokeWidth = 2.dp,
          modifier = Modifier.size(20.dp).testTag(PersonalInfoScreenTestTags.LOADER),
      )
    } else {
      Text(
          stringResource(
              if (state.isEditing) R.string.personal_info_save_changes
              else R.string.personal_info_create_profile
          ),
          style = jakarta(15, 20, Bold, SpottedTheme.colors.onAccent),
      )
    }
  }
}

/**
 * Stateful entry point of the Personal Info screen.
 *
 * Collects the [PersonalInfoViewModel] state, forwards user input to it, and calls [onSaved] once
 * the profile is saved. "Use my location" asks for the location permission (precise or approximate)
 * and, once granted, lets the ViewModel place the home pin and name the area. Tapping the avatar
 * offers the gallery (system Photo Picker) or the camera app, as for pet photos. Without [onBack]
 * (onboarding), no Back button is shown.
 */
@Composable
fun PersonalInfoScreen(
    viewModel: PersonalInfoViewModel = viewModel(factory = PersonalInfoViewModel.Factory),
    onBack: (() -> Unit)? = null,
    onSaved: () -> Unit = {},
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current

  var showPhotoOptions by rememberSaveable { mutableStateOf(false) }
  // Keep the failure local to this dialog so it can stay open and offer gallery recovery.
  var cameraError by rememberSaveable { mutableStateOf(false) }
  val photoPickerActions =
      rememberPhotoPickerActions(
          onPhotoSelected = { uri -> uri?.let { viewModel.onPhotoSelected(it.toString()) } },
          onCameraUnavailable = {
            cameraError = true
            showPhotoOptions = true
          },
      )

  val locationPermissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
          grants ->
        if (grants.values.any { it }) viewModel.useCurrentLocation()
        else viewModel.onLocationPermissionDenied()
      }

  val currentOnSaved by rememberUpdatedState(onSaved)
  LaunchedEffect(state.isSaved) {
    if (state.isSaved) {
      viewModel.onSavedHandled()
      // The profile photo keeps the same URL when replaced: drop the old one from Coil's cache
      context.imageLoader.memoryCache?.clear()
      currentOnSaved()
    }
  }

  PersonalInfoContent(
      state = state,
      isPickingPhoto = photoPickerActions.isPicking,
      onBackClick = onBack,
      onChangePhotoClick = {
        cameraError = false
        showPhotoOptions = true
      },
      onUsernameChange = viewModel::onUsernameChanged,
      onFirstNameChange = viewModel::onFirstNameChanged,
      onLastNameChange = viewModel::onLastNameChanged,
      onHomeAreaChange = viewModel::onHomeAreaChanged,
      onUseMyLocationClick = {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
      },
      onHomeLocationChange = viewModel::onHomeLocationChanged,
      onAlertRadiusSelect = viewModel::onAlertRadiusChanged,
      onCreateProfileClick = viewModel::saveProfile,
  )

  if (showPhotoOptions) {
    PhotoOptionsDialog(
        hasPhoto = state.photoUri != null,
        canTakePhoto = remember(context) { CameraPhotoFile.deviceHasCamera(context) },
        cameraError = cameraError,
        enabled = !state.isSaving && !photoPickerActions.isPicking,
        onPickPhoto = {
          cameraError = false
          showPhotoOptions = false
          photoPickerActions.pickFromGallery()
        },
        onTakePhoto = {
          cameraError = false
          showPhotoOptions = false
          photoPickerActions.takePhoto()
        },
        onRemovePhoto = {
          showPhotoOptions = false
          viewModel.onPhotoSelected(null)
        },
        onDismiss = {
          cameraError = false
          showPhotoOptions = false
        },
    )
  }
}

/** Where the profile photo comes from: the gallery or the camera, or remove the current one. */
@Composable
internal fun PhotoOptionsDialog(
    hasPhoto: Boolean,
    canTakePhoto: Boolean,
    cameraError: Boolean = false,
    enabled: Boolean = true,
    onPickPhoto: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onDismiss: () -> Unit,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = {
        Text(
            stringResource(R.string.profile_photo_title),
            style = jakarta(18, 24, Bold, SpottedTheme.colors.ink),
        )
      },
      text = {
        Column(Modifier.testTag(PersonalInfoScreenTestTags.PHOTO_OPTIONS)) {
          if (cameraError) {
            Text(
                stringResource(R.string.photo_camera_unavailable),
                style = jakarta(13, 18, FontWeight.Medium, SpottedTheme.colors.error),
            )
          }
          PhotoOption(
              stringResource(R.string.profile_photo_choose_gallery),
              PersonalInfoScreenTestTags.PHOTO_FROM_GALLERY,
              onPickPhoto,
              enabled,
          )
          if (canTakePhoto) {
            PhotoOption(
                stringResource(R.string.photo_take),
                PersonalInfoScreenTestTags.PHOTO_FROM_CAMERA,
                onTakePhoto,
                enabled,
            )
          }
          if (hasPhoto) {
            PhotoOption(
                stringResource(R.string.photo_remove),
                PersonalInfoScreenTestTags.PHOTO_REMOVE,
                onRemovePhoto,
                enabled,
            )
          }
        }
      },
      confirmButton = {},
      dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
}

@Composable
private fun PhotoOption(text: String, testTag: String, onClick: () -> Unit, enabled: Boolean) {
  TextButton(
      onClick = onClick,
      enabled = enabled,
      modifier = Modifier.fillMaxWidth().testTag(testTag),
  ) {
    Text(
        text,
        style = jakarta(15, 20, Bold, SpottedTheme.colors.blue),
        modifier = Modifier.fillMaxWidth(),
    )
  }
}
