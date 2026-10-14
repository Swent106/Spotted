// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Pet
import com.android.spotted.ui.components.MapPicker
import com.android.spotted.ui.theme.SpottedAvatarBackground
import com.android.spotted.ui.theme.SpottedBlueTint
import com.android.spotted.ui.theme.SpottedError
import com.android.spotted.ui.theme.SpottedScreenBackground
import com.android.spotted.ui.theme.jakarta

/** Test tags used by UI tests to find the elements of the Report missing screen. */
object ReportMissingScreenTestTags {
  const val SCREEN = "reportMissingScreen"
  const val BACK_BUTTON = "reportMissingBackButton"
  const val PET_OPTION_PREFIX = "reportMissingPetOption_"
  const val NO_PETS = "reportMissingNoPets"
  const val PUBLISH_BUTTON = "reportMissingPublishButton"
  const val PUBLISH_LOADER = "reportMissingPublishLoader"
  const val ERROR_MESSAGE = "reportMissingErrorMessage"
}

/** Radius of the circle on the map, matching "Everyone within 3 km" in the wireframe. */
const val ALERT_RADIUS_KM = 3

/**
 * Stateless "Report a pet missing" screen: the owner picks the missing pet and where they last saw
 * it, then publishes the alert. Every value comes from [state]; every tap goes through a callback.
 */
@Composable
fun ReportMissingContent(
    state: ReportMissingUiState,
    onBack: () -> Unit = {},
    onPetSelected: (String) -> Unit = {},
    onLocationChange: (Location) -> Unit = {},
    onPublish: () -> Unit = {},
    onDismissError: () -> Unit = {},
) {
  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(SpottedScreenBackground)
              .verticalScroll(rememberScrollState())
              .padding(16.dp)
              .testTag(ReportMissingScreenTestTags.SCREEN),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      TextButton(
          onClick = onBack,
          modifier = Modifier.testTag(ReportMissingScreenTestTags.BACK_BUTTON),
      ) {
        Text("<", style = jakarta(20, 24f, 700, 0xFF14161A))
      }
      Column {
        Text("Report a pet missing", style = jakarta(22, 28f, 700, 0xFF14161A))
        Text(
            "Everyone within $ALERT_RADIUS_KM km gets an alert right away",
            style = jakarta(12, 16f, 500, 0xFF5F6672),
        )
      }
    }

    Section("Which pet is missing?") {
      if (state.isLoadingPets) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
      } else if (state.pets.isEmpty()) {
        Text(
            "You have no pets yet. Add one from your profile first.",
            style = jakarta(13, 18f, 500, 0xFF5F6672),
            modifier = Modifier.testTag(ReportMissingScreenTestTags.NO_PETS),
        )
      }
      state.pets.forEach { pet ->
        PetOption(
            pet = pet,
            selected = pet.id == state.selectedPetId,
            onClick = { onPetSelected(pet.id) },
        )
      }
    }

    Section("Last seen here") {
      MapPicker(
          location = state.location,
          radiusKm = ALERT_RADIUS_KM,
          onLocationChange = onLocationChange,
          modifier = Modifier.fillMaxWidth().height(180.dp),
          hint = "Tap where you last saw your pet",
      )
    }

    state.errorMessage?.let { message ->
      Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth().testTag(ReportMissingScreenTestTags.ERROR_MESSAGE),
      ) {
        Text(message, style = jakarta(13, 18f, 600, 0xFFD93025), modifier = Modifier.weight(1f))
        TextButton(onClick = onDismissError) { Text("OK") }
      }
    }

    Button(
        onClick = onPublish,
        enabled = state.canPublish,
        colors = ButtonDefaults.buttonColors(containerColor = SpottedError),
        shape = RoundedCornerShape(16.dp),
        modifier =
            Modifier.fillMaxWidth()
                .height(56.dp)
                .testTag(ReportMissingScreenTestTags.PUBLISH_BUTTON),
    ) {
      if (state.isPublishing) {
        CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 2.dp,
            modifier = Modifier.size(20.dp).testTag(ReportMissingScreenTestTags.PUBLISH_LOADER),
        )
      } else {
        Text("Publish alert", style = jakarta(16, 20f, 700, 0xFFFFFFFF))
      }
    }
  }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
  Column(
      modifier =
          Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(title.uppercase(), style = jakarta(12, 16f, 700, 0xFF6B7280))
    content()
  }
}

@Composable
private fun PetOption(pet: Pet, selected: Boolean, onClick: () -> Unit) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
          Modifier.fillMaxWidth()
              .background(
                  if (selected) SpottedBlueTint else Color(0xFFF5F6F8),
                  RoundedCornerShape(16.dp),
              )
              .clickable(onClick = onClick)
              .padding(12.dp)
              .testTag("${ReportMissingScreenTestTags.PET_OPTION_PREFIX}${pet.id}"),
  ) {
    Box(
        modifier =
            Modifier.size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SpottedAvatarBackground),
        contentAlignment = Alignment.Center,
    ) {
      if (pet.photoUrl != null) {
        AsyncImage(
            model = pet.photoUrl,
            contentDescription = pet.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
      } else {
        Text(pet.name.take(1), style = jakarta(18, 22f, 700, 0xFF14161A))
      }
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(pet.name, style = jakarta(16, 20f, 700, 0xFF14161A))
      Text(
          pet.breed.ifBlank { pet.species.name.lowercase().replaceFirstChar { it.uppercase() } },
          style = jakarta(12, 16f, 500, 0xFF5F6672),
      )
    }
    RadioButton(selected = selected, onClick = onClick)
  }
}

/**
 * Connects [ReportMissingContent] to [ReportMissingViewModel]: passes the state down and the taps
 * up, and calls [onPublished] once the alert is out.
 */
@Composable
fun ReportMissingScreen(
    onBack: () -> Unit,
    onPublished: () -> Unit,
    viewModel: ReportMissingViewModel = viewModel(factory = ReportMissingViewModel.Factory),
) {
  val state by viewModel.uiState.collectAsState()

  LaunchedEffect(state.published) { if (state.published) onPublished() }

  ReportMissingContent(
      state = state,
      onBack = onBack,
      onPetSelected = viewModel::selectPet,
      onLocationChange = viewModel::setLocation,
      onPublish = viewModel::publish,
      onDismissError = viewModel::clearErrorMsg,
  )
}
