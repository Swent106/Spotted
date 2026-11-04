// Portions of this code were generated with the help of Copilot.
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.android.spotted.R
import com.android.spotted.ui.components.rememberPhotoPickerActions
import com.android.spotted.ui.theme.jakarta
import java.util.Locale

object ReportSightingScreenTestTags {
  const val SCREEN = "sightingScreen"
  const val PHOTO_AREA = "sightingPhotoArea"
  const val PHOTO_BUTTON = "sightingPhotoButton"
  const val LOCATION_CARD = "sightingLocationCard"
  const val PET_OPTION_PREFIX = "sightingPetOption_"
  const val NO_MATCH_OPTION = "sightingNoMatchOption"
  const val SUBMIT_BUTTON = "sightingSubmitButton"
  const val LOADER = "sightingLoader"
  const val ERROR_MESSAGE = "sightingErrorMessage"
}

@Composable
fun ReportSightingContent(
    state: ReportSightingUiState,
    onPhotoSelected: (Uri?) -> Unit = {},
    onLocationClick: () -> Unit = {},
    onAlertSelected: (String) -> Unit = {},
    onNoMatchSelected: () -> Unit = {},
    onSubmitClick: () -> Unit = {},
) {
  val photoPickerActions =
      rememberPhotoPickerActions(onPhotoSelected = onPhotoSelected, onCameraUnavailable = {})

  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(Color(0xFFEFEFEF))
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
                .background(Color(0xFF1E2026))
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
              style = jakarta(14, 18f, 600, 0xFF8E95A2),
          )
        }
      }

      // Retake / Add Photo Button in top right
      Button(
          onClick = { photoPickerActions.pickFromGallery() },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14161A)),
          shape = RoundedCornerShape(20.dp),
          modifier =
              Modifier.align(Alignment.TopEnd)
                  .padding(16.dp)
                  .testTag(ReportSightingScreenTestTags.PHOTO_BUTTON),
      ) {
        Text(
            stringResource(
                if (state.photoUri != null) R.string.sighting_photo_retake
                else R.string.sighting_photo_add
            ),
            style = jakarta(13, 16f, 600, 0xFFFFFFFF),
        )
      }

      // Species Detection Tag
      state.detectedSpecies?.let { species ->
        Box(
            modifier =
                Modifier.align(Alignment.BottomStart)
                    .padding(16.dp)
                    .background(Color.White, RoundedCornerShape(100.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
          Text(
              "Looks like a ${species.name.lowercase()} · you can correct this",
              style = jakarta(12, 16f, 600, 0xFF2C3340),
          )
        }
      }
    }

    // Bottom form area
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
          "New sighting",
          style = jakarta(26, 32f, 800, 0xFF14161A),
      )

      // Location Card
      Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
              Modifier.fillMaxWidth()
                  .background(Color.White, RoundedCornerShape(16.dp))
                  .clickable { onLocationClick() }
                  .padding(16.dp)
                  .testTag(ReportSightingScreenTestTags.LOCATION_CARD),
      ) {
        Box(
            modifier =
                Modifier.size(44.dp).background(Color(0xFFE8EEFC), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
          LocationPinIcon(modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
              state.locationAddress,
              style = jakarta(16, 20f, 700, 0xFF14161A),
          )
          Text(
              state.location?.let { "%.4f, %.4f".format(Locale.US, it.latitude, it.longitude) }
                  ?: "Tap to use your GPS position",
              style = jakarta(12, 16f, 500, 0xFF5F6672),
          )
        }
      }

      Text(
          "WHICH PET DID YOU SEE?",
          style = jakarta(12, 16f, 700, 0xFF8E95A2),
          modifier = Modifier.padding(top = 8.dp),
      )

      // Nearby Alerts list
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
                                if (isSelected) Color(0xFF2563EB) else Color(0xFFE5E7EB),
                            ),
                        shape = RoundedCornerShape(16.dp),
                    )
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .clickable { onAlertSelected(alert.alertId) }
                    .padding(16.dp)
                    .testTag("${ReportSightingScreenTestTags.PET_OPTION_PREFIX}${alert.alertId}"),
        ) {
          Box(
              modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFF0E0CE)),
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
              Text(alert.petName.take(1), style = jakarta(18, 22f, 700, 0xFF14161A))
            }
          }
          Spacer(Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(alert.petName, style = jakarta(16, 20f, 700, 0xFF14161A))
            Text(alert.description, style = jakarta(12, 16f, 500, 0xFF5F6672))
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
                              if (state.isNoMatchingAlertSelected) Color(0xFF2563EB)
                              else Color(0xFFE5E7EB),
                          ),
                      shape = RoundedCornerShape(16.dp),
                  )
                  .background(Color.White, RoundedCornerShape(16.dp))
                  .clickable { onNoMatchSelected() }
                  .padding(16.dp)
                  .testTag(ReportSightingScreenTestTags.NO_MATCH_OPTION),
      ) {
        Box(
            modifier =
                Modifier.size(44.dp).background(Color(0xFFF3F4F6), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
          Text("?", style = jakarta(20, 24f, 700, 0xFF4B5563))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text("No matching alert", style = jakarta(16, 20f, 700, 0xFF14161A))
          Text("Report it as a stray I saw", style = jakarta(12, 16f, 500, 0xFF5F6672))
        }
        RadioSelectionIndicator(isSelected = state.isNoMatchingAlertSelected)
      }

      // Offline Banner
      if (state.isOffline) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.fillMaxWidth()
                    .background(Color(0xFFFEF3C7), RoundedCornerShape(12.dp))
                    .padding(14.dp),
        ) {
          Text("⚠", style = jakarta(18, 22f, 700, 0xFFD97706))
          Spacer(Modifier.width(10.dp))
          Column {
            Text("You are offline", style = jakarta(13, 18f, 700, 0xFF92400E))
            Text(
                "Saved on your phone, sent when signal is back",
                style = jakarta(11, 15f, 500, 0xFFB45309),
            )
          }
        }
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
              color = Color(0xFF2563EB),
          )
        }
      } else {
        Button(
            onClick = onSubmitClick,
            enabled = state.canSubmit,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2563EB),
                    disabledContainerColor = Color(0xFF93C5FD),
                ),
            shape = RoundedCornerShape(12.dp),
            modifier =
                Modifier.fillMaxWidth()
                    .height(52.dp)
                    .testTag(ReportSightingScreenTestTags.SUBMIT_BUTTON),
        ) {
          Text(
              "Send sighting",
              style = jakarta(16, 20f, 700, 0xFFFFFFFF),
          )
        }
      }

      // Error Message
      state.errorMessage?.let { msg ->
        Text(
            msg,
            modifier = Modifier.fillMaxWidth().testTag(ReportSightingScreenTestTags.ERROR_MESSAGE),
            style = jakarta(13, 18f, 600, 0xFFD93025, TextAlign.Center),
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
                  if (isSelected) Color(0xFF2563EB) else Color.Transparent,
                  CircleShape,
              )
              .border(
                  1.5.dp,
                  if (isSelected) Color(0xFF2563EB) else Color(0xFF9CA3AF),
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
    drawPath(path = path, color = Color(0xFF2563EB))
  }
}

@Composable
private fun CheckIcon(modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val path =
        Path().apply {
          moveTo(size.width * 0.2f, size.height * 0.5f)
          lineTo(size.width * 0.45f, size.height * 0.75f)
          lineTo(size.width * 0.8f, size.height * 0.25f)
        }
    drawPath(
        path = path,
        color = Color.White,
        style = Stroke(width = size.width * 0.2f),
    )
  }
}

/** Stateful wrapper for [ReportSightingContent] handling location permission. */
@Composable
fun ReportSightingScreen(
    viewModel: ReportSightingViewModel = viewModel(factory = ReportSightingViewModel.Factory),
    onSubmitted: () -> Unit = {},
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
      onSubmitClick = viewModel::submitSighting,
  )
}
