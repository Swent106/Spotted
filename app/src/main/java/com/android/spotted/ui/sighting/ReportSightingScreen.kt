// Portions of this code were generated with the help of Claude (Anthropic).
// Portions of this code were generated with the help of Copilot.
// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.sighting

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.android.spotted.R
import com.android.spotted.ui.components.SpottedBackButton
import com.android.spotted.ui.components.rememberPhotoPickerActions
import com.android.spotted.ui.theme.SpottedIllustration
import com.android.spotted.ui.theme.SpottedTheme
import com.android.spotted.ui.theme.jakarta
import java.util.Locale
import kotlin.math.roundToInt

object ReportSightingScreenTestTags {
  const val SCREEN = "sightingScreen"
  const val BACK_BUTTON = "sightingBackButton"
  const val PHOTO_AREA = "sightingPhotoArea"
  const val PHOTO_BUTTON = "sightingPhotoButton"
  const val LOCATION_CARD = "sightingLocationCard"
  const val PET_OPTION_PREFIX = "sightingPetOption_"
  const val NO_MATCH_OPTION = "sightingNoMatchOption"
  const val NO_NEARBY_MATCHES = "sightingNoNearbyMatches"
  const val SUBMIT_BUTTON = "sightingSubmitButton"
  const val LOADER = "sightingLoader"
  const val ERROR_MESSAGE = "sightingErrorMessage"
  const val PHOTO_CHECKING = "sighting_photoChecking"
  const val PHOTO_WARNING = "sighting_photoWarning"
  const val PHOTO_REQUIRED = "sighting_photoRequired"
  const val OFFLINE_BANNER = "reportSighting_offlineBanner"
  const val SAVED_DIALOG = "reportSighting_savedDialog"
  const val BACK_HOME = "reportSighting_backHome"
}

@Composable
fun ReportSightingContent(
    state: ReportSightingUiState,
    onPhotoSelected: (Uri?) -> Unit = {},
    onLocationClick: () -> Unit = {},
    onAlertSelected: (String) -> Unit = {},
    onNoMatchSelected: () -> Unit = {},
    onDirectionSelected: (Double?) -> Unit = {},
    onSubmitClick: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    onSavedOfflineDone: () -> Unit = {},
) {
  val photoPickerActions =
      rememberPhotoPickerActions(onPhotoSelected = onPhotoSelected, onCameraUnavailable = {})

  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(SpottedTheme.colors.screenBackground)
              .paint(
                  painterResource(R.drawable.background_textures),
                  contentScale = ContentScale.Crop,
              )
              .verticalScroll(rememberScrollState())
              .testTag(ReportSightingScreenTestTags.SCREEN)
  ) {
    // Top photo area (dark background)
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(260.dp)
                .background(SpottedIllustration.cameraBackground)
                .testTag(ReportSightingScreenTestTags.PHOTO_AREA),
        contentAlignment = Alignment.Center,
    ) {
      if (state.photoUri != null) {
        AsyncImage(
            model = state.photoUri,
            contentDescription = "Animal photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
      } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
          Text(
              "[ PHOTO OF THE ANIMAL ]",
              style = jakarta(14, 18f, 600, SpottedTheme.colors.textMuted),
          )
        }
      }

      // Back to the previous screen, over the photo as in Alert detail
      onBack?.let {
        SpottedBackButton(
            onClick = it,
            testTag = ReportSightingScreenTestTags.BACK_BUTTON,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        )
      }

      // Retake / Add Photo Button in top right
      Button(
          onClick = { photoPickerActions.pickFromGallery() },
          colors =
              ButtonDefaults.buttonColors(containerColor = SpottedIllustration.cameraBackground),
          shape = RoundedCornerShape(20.dp),
          modifier =
              Modifier.align(Alignment.TopEnd)
                  .padding(16.dp)
                  .testTag(ReportSightingScreenTestTags.PHOTO_BUTTON),
          enabled = !state.isSubmitting && !photoPickerActions.isPicking,
      ) {
        Text(
            stringResource(
                if (state.photoUri != null) R.string.sighting_photo_retake
                else R.string.sighting_photo_add
            ),
            style = jakarta(13, 16f, 600, SpottedTheme.colors.onAccent),
        )
      }

      // Species Detection Tag
      state.detectedSpecies?.let { species ->
        Box(
            modifier =
                Modifier.align(Alignment.BottomStart)
                    .padding(16.dp)
                    .background(SpottedTheme.colors.surface, RoundedCornerShape(100.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
          Text(
              "Looks like a ${species.name.lowercase()} · you can correct this",
              style = jakarta(12, 16f, 600, SpottedTheme.colors.inkSoft),
          )
        }
      }
      if (state.photoCheck == PhotoCheck.CHECKING) {
        CircularProgressIndicator(
            modifier =
                Modifier.align(Alignment.Center)
                    .size(36.dp)
                    .testTag(ReportSightingScreenTestTags.PHOTO_CHECKING)
                    .semantics { contentDescription = "Checking the photo" },
            color = SpottedTheme.colors.onAccent,
        )
      }
      if (state.isPhotoMissing) {
        Text(
            stringResource(R.string.sighting_photo_required),
            style = jakarta(12, 16f, 600, SpottedTheme.colors.inkSoft),
            modifier =
                Modifier.align(Alignment.BottomStart)
                    .padding(16.dp)
                    .background(SpottedTheme.colors.surface, RoundedCornerShape(100.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .testTag(ReportSightingScreenTestTags.PHOTO_REQUIRED),
        )
      }
      if (state.photoCheck == PhotoCheck.NO_ANIMAL) {
        Text(
            stringResource(R.string.sighting_photo_no_animal),
            style = jakarta(12, 16f, 600, SpottedTheme.colors.inkSoft),
            modifier =
                Modifier.align(Alignment.BottomStart)
                    .padding(16.dp)
                    .background(SpottedTheme.colors.surface, RoundedCornerShape(100.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .testTag(ReportSightingScreenTestTags.PHOTO_WARNING),
        )
      }
    }

    // Bottom form area
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
          "New sighting",
          style = jakarta(26, 32f, 800, SpottedTheme.colors.ink),
      )

      // Location Card
      Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
              Modifier.fillMaxWidth()
                  .background(SpottedTheme.colors.surface, RoundedCornerShape(16.dp))
                  .clickable { onLocationClick() }
                  .padding(16.dp)
                  .testTag(ReportSightingScreenTestTags.LOCATION_CARD),
      ) {
        Box(
            modifier =
                Modifier.size(44.dp)
                    .background(SpottedTheme.colors.mapBackground, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
          LocationPinIcon(modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
              state.locationAddress,
              style = jakarta(16, 20f, 700, SpottedTheme.colors.ink),
          )
          Text(
              state.location?.let { "%.4f, %.4f".format(Locale.US, it.latitude, it.longitude) }
                  ?: "Tap to use your GPS position",
              style = jakarta(12, 16f, 500, SpottedTheme.colors.textSecondary),
          )
        }
      }

      Text(
          "WHICH PET DID YOU SEE?",
          style = jakarta(12, 16f, 700, SpottedTheme.colors.textMuted),
          modifier = Modifier.padding(top = 8.dp),
      )

      // Nearby Alerts list
      if (state.isNearbyAlertsLoaded && state.nearbyAlerts.isEmpty()) {
        Text(
            stringResource(R.string.sighting_no_missing_pet_matches_nearby),
            style = jakarta(14, 18f, 500, SpottedTheme.colors.textSecondary),
            modifier =
                Modifier.padding(vertical = 8.dp)
                    .testTag(ReportSightingScreenTestTags.NO_NEARBY_MATCHES),
        )
      }
      state.nearbyAlerts.forEach { alert ->
        val isSelected = state.selectedAlertId == alert.alertId && !state.isNoMatchingAlertSelected
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.fillMaxWidth()
                    .border(
                        border =
                            BorderStroke(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) SpottedTheme.colors.blue
                                else SpottedTheme.colors.buttonBorder,
                            ),
                        shape = RoundedCornerShape(16.dp),
                    )
                    .background(SpottedTheme.colors.surface, RoundedCornerShape(16.dp))
                    .clickable { onAlertSelected(alert.alertId) }
                    .padding(16.dp)
                    .testTag("${ReportSightingScreenTestTags.PET_OPTION_PREFIX}${alert.alertId}"),
        ) {
          Box(
              modifier =
                  Modifier.size(44.dp)
                      .clip(CircleShape)
                      .background(SpottedTheme.colors.featuredPhotoBackground),
              contentAlignment = Alignment.Center,
          ) {
            if (alert.photoUrl != null) {
              AsyncImage(
                  model = alert.photoUrl,
                  contentDescription = alert.petName,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize(),
              )
            } else {
              Text(alert.petName.take(1), style = jakarta(18, 22f, 700, SpottedTheme.colors.ink))
            }
          }
          Spacer(Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(alert.petName, style = jakarta(16, 20f, 700, SpottedTheme.colors.ink))
            Text(
                alert.description,
                style = jakarta(12, 16f, 500, SpottedTheme.colors.textSecondary),
            )
          }
          RadioSelectionIndicator(isSelected = isSelected)
        }
      }

      // "No matching alert" Option
      Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
              Modifier.fillMaxWidth()
                  .border(
                      border =
                          BorderStroke(
                              if (state.isNoMatchingAlertSelected) 2.dp else 1.dp,
                              if (state.isNoMatchingAlertSelected) SpottedTheme.colors.blue
                              else SpottedTheme.colors.buttonBorder,
                          ),
                      shape = RoundedCornerShape(16.dp),
                  )
                  .background(SpottedTheme.colors.surface, RoundedCornerShape(16.dp))
                  .clickable { onNoMatchSelected() }
                  .padding(16.dp)
                  .testTag(ReportSightingScreenTestTags.NO_MATCH_OPTION),
      ) {
        Box(
            modifier =
                Modifier.size(44.dp)
                    .background(SpottedTheme.colors.inputBackground, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
          Text("?", style = jakarta(20, 24f, 700, SpottedTheme.colors.inkSoft))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text("No matching alert", style = jakarta(16, 20f, 700, SpottedTheme.colors.ink))
          Text(
              "Report it as a stray I saw",
              style = jakarta(12, 16f, 500, SpottedTheme.colors.textSecondary),
          )
        }
        RadioSelectionIndicator(isSelected = state.isNoMatchingAlertSelected)
      }

      DirectionPicker(
          selectedHeadingDegrees = state.direction?.toDouble(),
          onDirectionSelected = onDirectionSelected,
      )

      // Offline banner: stays under the "Saved on your phone" dialog, as in the Figma
      if (state.isOffline) {
        OfflineBanner()
      }

      // Loader or Submit Button
      if (state.isSubmitting) {
        Box(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(
              modifier =
                  Modifier.size(36.dp).testTag(ReportSightingScreenTestTags.LOADER).semantics {
                    contentDescription = "Submitting sighting"
                  },
              color = SpottedTheme.colors.blue,
          )
        }
      } else {
        Button(
            onClick = onSubmitClick,
            enabled = state.canSubmit,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = SpottedTheme.colors.blue,
                    disabledContainerColor = SpottedTheme.colors.blue.copy(alpha = 0.45f),
                ),
            shape = RoundedCornerShape(12.dp),
            modifier =
                Modifier.fillMaxWidth()
                    .height(52.dp)
                    .testTag(ReportSightingScreenTestTags.SUBMIT_BUTTON),
        ) {
          Text(
              "Send sighting",
              style = jakarta(16, 20f, 700, SpottedTheme.colors.onAccent),
          )
        }
      }

      // Error Message
      state.errorMessage?.let { msg ->
        Text(
            msg,
            modifier = Modifier.fillMaxWidth().testTag(ReportSightingScreenTestTags.ERROR_MESSAGE),
            style = jakarta(13, 18f, 600, SpottedTheme.colors.error, TextAlign.Center),
        )
      }
    }
  }

  if (state.savedOffline) {
    val petName =
        state.nearbyAlerts
            .firstOrNull { it.alertId == state.selectedAlertId }
            ?.petName
            .takeIf { !state.isNoMatchingAlertSelected }
    SavedOfflineDialog(petName = petName, onDone = onSavedOfflineDone)
  }
}

/** "You are offline" banner, from the Figma frame "10 · Report a sighting". */
@Composable
private fun OfflineBanner() {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
          Modifier.fillMaxWidth()
              .background(SpottedTheme.colors.warningTint, RoundedCornerShape(12.dp))
              .padding(14.dp)
              .testTag(ReportSightingScreenTestTags.OFFLINE_BANNER),
  ) {
    Icon(
        painterResource(R.drawable.ic_cloud_off),
        contentDescription = null,
        tint = SpottedTheme.colors.warning,
        modifier = Modifier.size(22.dp),
    )
    Spacer(Modifier.width(10.dp))
    Column {
      Text(
          stringResource(R.string.sighting_offline_title),
          style = jakarta(13, 18f, 700, SpottedTheme.colors.warning),
      )
      Text(
          stringResource(R.string.sighting_offline_text),
          style = jakarta(11, 15f, 500, SpottedTheme.colors.warning),
      )
    }
  }
}

/**
 * "Saved on your phone", from the Figma frame "10c · Report a sighting · Saved offline": shown once
 * the sighting is queued. [petName] is the pet of the chosen alert, if any. Back to Home, or
 * dismissing it, calls [onDone].
 */
@Composable
private fun SavedOfflineDialog(petName: String?, onDone: () -> Unit) {
  Dialog(onDismissRequest = onDone) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier.fillMaxWidth()
                .background(SpottedTheme.colors.surface, RoundedCornerShape(24.dp))
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 18.dp)
                .testTag(ReportSightingScreenTestTags.SAVED_DIALOG),
    ) {
      Box(
          modifier = Modifier.size(64.dp).background(SpottedTheme.colors.warningTint, CircleShape),
          contentAlignment = Alignment.Center,
      ) {
        Icon(
            painterResource(R.drawable.ic_cloud_off),
            contentDescription = null,
            tint = SpottedTheme.colors.warning,
            modifier = Modifier.size(30.dp),
        )
      }
      Text(
          stringResource(R.string.sighting_saved_offline_title),
          style = jakarta(20, 25f, 800, SpottedTheme.colors.ink, TextAlign.Center),
      )
      Text(
          if (petName != null) stringResource(R.string.sighting_saved_offline_text_pet, petName)
          else stringResource(R.string.sighting_saved_offline_text),
          style = jakarta(13, 18f, 500, SpottedTheme.colors.textSecondary, TextAlign.Center),
      )
      Button(
          onClick = onDone,
          colors = ButtonDefaults.buttonColors(containerColor = SpottedTheme.colors.blue),
          shape = RoundedCornerShape(14.dp),
          modifier =
              Modifier.fillMaxWidth().height(49.dp).testTag(ReportSightingScreenTestTags.BACK_HOME),
      ) {
        Text(
            stringResource(R.string.sighting_back_home),
            style = jakarta(15, 19f, 700, SpottedTheme.colors.onAccent),
        )
      }
    }
  }
}

@Composable
private fun RadioSelectionIndicator(isSelected: Boolean) {
  Box(
      modifier =
          Modifier.size(24.dp)
              .background(
                  if (isSelected) SpottedTheme.colors.blue else Color.Transparent,
                  CircleShape,
              )
              .border(
                  1.5.dp,
                  if (isSelected) SpottedTheme.colors.blue else SpottedTheme.colors.textMuted,
                  CircleShape,
              ),
      contentAlignment = Alignment.Center,
  ) {
    if (isSelected) {
      CheckIcon(modifier = Modifier.size(14.dp))
    }
  }
}

@Composable
private fun LocationPinIcon(modifier: Modifier = Modifier) {
  // Read before drawing: the Canvas lambda is not composable
  val pinColor = SpottedTheme.colors.blue
  Canvas(modifier = modifier) {
    val path =
        Path().apply {
          moveTo(size.width * 0.5f, size.height * 0.1f)
          cubicTo(
              size.width * 0.25f,
              size.height * 0.1f,
              size.width * 0.1f,
              size.height * 0.35f,
              size.width * 0.1f,
              size.height * 0.55f,
          )
          cubicTo(
              size.width * 0.1f,
              size.height * 0.75f,
              size.width * 0.5f,
              size.height * 0.95f,
              size.width * 0.5f,
              size.height * 0.95f,
          )
          cubicTo(
              size.width * 0.5f,
              size.height * 0.95f,
              size.width * 0.9f,
              size.height * 0.75f,
              size.width * 0.9f,
              size.height * 0.55f,
          )
          cubicTo(
              size.width * 0.9f,
              size.height * 0.35f,
              size.width * 0.75f,
              size.height * 0.1f,
              size.width * 0.5f,
              size.height * 0.1f,
          )
          close()
        }
    drawPath(path = path, color = pinColor)
  }
}

@Composable
private fun CheckIcon(modifier: Modifier = Modifier) {
  // Read before drawing: the Canvas lambda is not composable
  val checkColor = SpottedTheme.colors.onAccent
  Canvas(modifier = modifier) {
    val path =
        Path().apply {
          moveTo(size.width * 0.2f, size.height * 0.5f)
          lineTo(size.width * 0.45f, size.height * 0.75f)
          lineTo(size.width * 0.8f, size.height * 0.25f)
        }
    drawPath(
        path = path,
        color = checkColor,
        style = Stroke(width = size.width * 0.2f),
    )
  }
}

/** Stateful wrapper for [ReportSightingContent] handling location permission. */
@Composable
fun ReportSightingScreen(
    viewModel: ReportSightingViewModel = viewModel(factory = ReportSightingViewModel.Factory),
    onSubmitted: () -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
  val state by viewModel.uiState.collectAsState()

  LaunchedEffect(state.submitted) {
    if (state.submitted) {
      onSubmitted()
    }
  }

  val permissionLauncher =
      rememberLauncherForActivityResult(
          contract = ActivityResultContracts.RequestPermission(),
          onResult = { isGranted ->
            if (isGranted) {
              viewModel.loadCurrentLocation()
            }
          },
      )

  ReportSightingContent(
      state = state,
      onPhotoSelected = { uri -> viewModel.setPhotoUri(uri?.toString()) },
      onLocationClick = {
        permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
      },
      onAlertSelected = viewModel::selectAlert,
      onNoMatchSelected = viewModel::selectNoMatchingAlert,
      onDirectionSelected = { degrees -> viewModel.setDirection(degrees?.roundToInt()) },
      onSubmitClick = viewModel::submitSighting,
      onBack = onBack,
      // The sighting is queued: the form is done, as after an online send
      onSavedOfflineDone = onSubmitted,
  )
}
