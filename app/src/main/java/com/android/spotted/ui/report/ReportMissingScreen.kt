// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.report

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.android.spotted.R
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.alert.LossCause
import com.android.spotted.model.alert.LossEnvironment
import com.android.spotted.ui.addpets.ChipFlow
import com.android.spotted.ui.addpets.FormChip
import com.android.spotted.ui.components.MapPicker
import com.android.spotted.ui.components.SpottedTopBar
import com.android.spotted.ui.theme.SpottedTheme
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
  const val ALREADY_MISSING_PREFIX = "reportMissingAlreadyMissing_"
  const val ALL_PETS_MISSING = "reportMissingAllPetsMissing"
  const val ALREADY_MISSING_DIALOG = "reportMissingAlreadyMissingDialog"
  const val OPEN_ALERT_BUTTON = "reportMissingOpenAlertButton"
  const val DISMISS_ALREADY_MISSING_BUTTON = "reportMissingDismissAlreadyMissingButton"

  // The "What happened?" step
  const val WHAT_HAPPENED = "reportMissing_whatHappened"
  const val WHAT_HAPPENED_BACK = "reportMissing_whatHappenedBack"
  const val WHAT_HAPPENED_PUBLISH = "reportMissing_whatHappenedPublish"
  const val SKIP = "reportMissing_skip"

  fun causeChip(cause: LossCause) = "reportMissing_causeChip_${cause.name}"

  fun environmentChip(environment: LossEnvironment) =
      "reportMissing_environmentChip_${environment.name}"
}

/** Radius of the circle on the map, matching "Everyone within 3 km" in the wireframe. */
const val ALERT_RADIUS_KM = 3

/**
 * Stateless "Report a pet missing" screen: the owner picks the missing pet and where they last saw
 * it, then [onOpenWhatHappened] shows the optional "What happened?" step, from which the alert is
 * published ([onPublish]) or the step skipped ([onSkip]). Every value comes from [state]; every tap
 * goes through a callback.
 */
@Composable
fun ReportMissingContent(
    state: ReportMissingUiState,
    onBack: () -> Unit = {},
    onPetSelected: (String) -> Unit = {},
    onLocationChange: (Location) -> Unit = {},
    onOpenWhatHappened: () -> Unit = {},
    onCauseSelected: (LossCause) -> Unit = {},
    onEnvironmentSelected: (LossEnvironment) -> Unit = {},
    onBackToForm: () -> Unit = {},
    onSkip: () -> Unit = {},
    onPublish: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onOpenExistingAlert: () -> Unit = {},
    onDismissAlreadyMissing: () -> Unit = {},
) {
  state.alreadyMissingPet?.let { pet ->
    AlreadyMissingDialog(pet.name, onOpenExistingAlert, onDismissAlreadyMissing)
  }

  if (state.showWhatHappened) {
    WhatHappenedStep(
        state = state,
        onCauseSelected = onCauseSelected,
        onEnvironmentSelected = onEnvironmentSelected,
        onBack = onBackToForm,
        onSkip = onSkip,
        onPublish = onPublish,
        onDismissError = onDismissError,
    )
    return
  }

  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(SpottedTheme.colors.screenBackground)
              .paint(
                  painterResource(R.drawable.background_textures),
                  contentScale = ContentScale.Crop,
              )
              .verticalScroll(rememberScrollState())
              .padding(16.dp)
              .testTag(ReportMissingScreenTestTags.SCREEN),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    SpottedTopBar(
        title = "Report a pet missing",
        subtitle = "Everyone within $ALERT_RADIUS_KM km gets an alert right away",
        onBack = onBack,
        backTestTag = ReportMissingScreenTestTags.BACK_BUTTON,
    )

    Section("Which pet is missing?") {
      if (state.isLoadingPets) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
      } else if (state.pets.isEmpty()) {
        Text(
            "You have no pets yet. Add one from your profile first.",
            style = jakarta(13, 18f, 500, SpottedTheme.colors.textSecondary),
            modifier = Modifier.testTag(ReportMissingScreenTestTags.NO_PETS),
        )
      } else if (state.allPetsMissing) {
        Text(
            "All your pets are already reported missing.",
            style = jakarta(13, 18f, 500, SpottedTheme.colors.textSecondary),
            modifier = Modifier.testTag(ReportMissingScreenTestTags.ALL_PETS_MISSING),
        )
      }
      state.pets.forEach { pet ->
        PetOption(
            pet = pet,
            selected = pet.id == state.selectedPet?.id,
            alreadyMissing = state.isAlreadyMissing(pet),
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

    state.errorMessage?.let { ErrorMessage(it, onDismissError) }

    PublishButton(
        enabled = state.canPublish,
        isPublishing = state.isPublishing,
        testTag = ReportMissingScreenTestTags.PUBLISH_BUTTON,
        onClick = onOpenWhatHappened,
    )
  }
}

/**
 * The optional "What happened?" step: how the pet got lost and the kind of place, as chips. Tapping
 * the chosen chip again unselects it ([LossCause.UNKNOWN] / [LossEnvironment.UNKNOWN]). The system
 * back goes back to the form, like the top bar arrow.
 */
@Composable
private fun WhatHappenedStep(
    state: ReportMissingUiState,
    onCauseSelected: (LossCause) -> Unit,
    onEnvironmentSelected: (LossEnvironment) -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onPublish: () -> Unit,
    onDismissError: () -> Unit,
) {
  BackHandler(onBack = onBack)
  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(SpottedTheme.colors.screenBackground)
              .paint(
                  painterResource(R.drawable.background_textures),
                  contentScale = ContentScale.Crop,
              )
              .verticalScroll(rememberScrollState())
              .padding(16.dp)
              .testTag(ReportMissingScreenTestTags.WHAT_HAPPENED),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    SpottedTopBar(
        title = stringResource(R.string.report_missing_what_happened_title),
        subtitle = stringResource(R.string.report_missing_what_happened_subtitle),
        onBack = onBack,
        backTestTag = ReportMissingScreenTestTags.WHAT_HAPPENED_BACK,
    )

    Section(stringResource(R.string.report_missing_cause_section)) {
      ChipFlow {
        LossCause.entries
            .filter { it != LossCause.UNKNOWN }
            .forEach { cause ->
              val selected = cause == state.cause
              FormChip(
                  text = stringResource(cause.labelRes()),
                  selected = selected,
                  role = Role.RadioButton,
                  testTag = ReportMissingScreenTestTags.causeChip(cause),
                  onClick = { onCauseSelected(if (selected) LossCause.UNKNOWN else cause) },
              )
            }
      }
    }

    Section(stringResource(R.string.report_missing_environment_section)) {
      ChipFlow {
        LossEnvironment.entries
            .filter { it != LossEnvironment.UNKNOWN }
            .forEach { environment ->
              val selected = environment == state.environment
              FormChip(
                  text = stringResource(environment.labelRes()),
                  selected = selected,
                  role = Role.RadioButton,
                  testTag = ReportMissingScreenTestTags.environmentChip(environment),
                  onClick = {
                    onEnvironmentSelected(if (selected) LossEnvironment.UNKNOWN else environment)
                  },
              )
            }
      }
    }

    state.errorMessage?.let { ErrorMessage(it, onDismissError) }

    PublishButton(
        enabled = state.canPublish,
        isPublishing = state.isPublishing,
        testTag = ReportMissingScreenTestTags.WHAT_HAPPENED_PUBLISH,
        onClick = onPublish,
    )
    TextButton(
        onClick = onSkip,
        enabled = state.canPublish,
        modifier = Modifier.fillMaxWidth().testTag(ReportMissingScreenTestTags.SKIP),
    ) {
      Text(
          stringResource(R.string.report_missing_skip),
          style = jakarta(14, 18f, 700, SpottedTheme.colors.textSecondary),
      )
    }
  }
}

@StringRes
private fun LossCause.labelRes(): Int =
    when (this) {
      LossCause.ESCAPED_FROM_HOME -> R.string.report_missing_cause_escaped_from_home
      LossCause.DURING_A_WALK -> R.string.report_missing_cause_during_a_walk
      LossCause.SCARED_BY_NOISE -> R.string.report_missing_cause_scared_by_noise
      LossCause.DURING_MOVE_OR_TRIP -> R.string.report_missing_cause_during_move_or_trip
      LossCause.STOLEN -> R.string.report_missing_cause_stolen
      LossCause.UNKNOWN -> R.string.report_missing_unknown
    }

@StringRes
private fun LossEnvironment.labelRes(): Int =
    when (this) {
      LossEnvironment.CITY_CENTRE -> R.string.report_missing_environment_city_centre
      LossEnvironment.SUBURB -> R.string.report_missing_environment_suburb
      LossEnvironment.VILLAGE_OR_RURAL -> R.string.report_missing_environment_village_or_rural
      LossEnvironment.FOREST_OR_MOUNTAIN -> R.string.report_missing_environment_forest_or_mountain
      LossEnvironment.UNKNOWN -> R.string.report_missing_unknown
    }

@Composable
private fun ErrorMessage(message: String, onDismiss: () -> Unit) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().testTag(ReportMissingScreenTestTags.ERROR_MESSAGE),
  ) {
    Text(
        message,
        style = jakarta(13, 18f, 600, SpottedTheme.colors.error),
        modifier = Modifier.weight(1f),
    )
    TextButton(onClick = onDismiss) { Text("OK") }
  }
}

@Composable
private fun PublishButton(
    enabled: Boolean,
    isPublishing: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
  Button(
      onClick = onClick,
      enabled = enabled,
      colors = ButtonDefaults.buttonColors(containerColor = SpottedTheme.colors.error),
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.fillMaxWidth().height(56.dp).testTag(testTag),
  ) {
    if (isPublishing) {
      CircularProgressIndicator(
          color = SpottedTheme.colors.onAccent,
          strokeWidth = 2.dp,
          modifier = Modifier.size(20.dp).testTag(ReportMissingScreenTestTags.PUBLISH_LOADER),
      )
    } else {
      Text("Publish alert", style = jakarta(16, 20f, 700, SpottedTheme.colors.onAccent))
    }
  }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .background(SpottedTheme.colors.surface, RoundedCornerShape(20.dp))
              .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(title.uppercase(), style = jakarta(12, 16f, 700, SpottedTheme.colors.textLabel))
    content()
  }
}

@Composable
private fun AlreadyMissingDialog(petName: String, onOpenAlert: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("$petName is already reported missing") },
      text = { Text("Open its alert to follow the sightings.") },
      confirmButton = {
        TextButton(
            onClick = onOpenAlert,
            modifier = Modifier.testTag(ReportMissingScreenTestTags.OPEN_ALERT_BUTTON),
        ) {
          Text("Open alert")
        }
      },
      dismissButton = {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag(ReportMissingScreenTestTags.DISMISS_ALREADY_MISSING_BUTTON),
        ) {
          Text("Cancel")
        }
      },
      modifier = Modifier.testTag(ReportMissingScreenTestTags.ALREADY_MISSING_DIALOG),
  )
}

/** A pet to choose. A pet [alreadyMissing] can't be chosen; tapping it offers its alert. */
@Composable
private fun PetOption(pet: Pet, selected: Boolean, alreadyMissing: Boolean, onClick: () -> Unit) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
          Modifier.fillMaxWidth()
              .background(
                  if (selected) SpottedTheme.colors.blueTint
                  else SpottedTheme.colors.inputBackground,
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
                .background(SpottedTheme.colors.avatarBackground),
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
        Text(pet.name.take(1), style = jakarta(18, 22f, 700, SpottedTheme.colors.ink))
      }
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(pet.name, style = jakarta(16, 20f, 700, SpottedTheme.colors.ink))
      if (alreadyMissing) {
        Text(
            "Already reported missing",
            style = jakarta(12, 16f, 600, SpottedTheme.colors.error),
            modifier =
                Modifier.testTag("${ReportMissingScreenTestTags.ALREADY_MISSING_PREFIX}${pet.id}"),
        )
      } else {
        Text(
            pet.breed.ifBlank { pet.species.name.lowercase().replaceFirstChar { it.uppercase() } },
            style = jakarta(12, 16f, 500, SpottedTheme.colors.textSecondary),
        )
      }
    }
    RadioButton(selected = selected, onClick = onClick, enabled = !alreadyMissing)
  }
}

/**
 * Connects [ReportMissingContent] to [ReportMissingViewModel]: passes the state down and the taps
 * up, and calls [onPublished] once the alert is out, or [onOpenAlert] with the alert of a pet
 * already missing that the owner chose to open.
 */
@Composable
fun ReportMissingScreen(
    onBack: () -> Unit,
    onPublished: () -> Unit,
    onOpenAlert: (alertId: String) -> Unit = {},
    viewModel: ReportMissingViewModel = viewModel(factory = ReportMissingViewModel.Factory),
) {
  val state by viewModel.uiState.collectAsState()

  LaunchedEffect(state.published) { if (state.published) onPublished() }
  LaunchedEffect(state.openAlertId) { state.openAlertId?.let(onOpenAlert) }

  ReportMissingContent(
      state = state,
      onBack = onBack,
      onPetSelected = viewModel::selectPet,
      onLocationChange = viewModel::setLocation,
      onOpenWhatHappened = viewModel::openWhatHappened,
      onCauseSelected = viewModel::setCause,
      onEnvironmentSelected = viewModel::setEnvironment,
      onBackToForm = viewModel::backToForm,
      onSkip = viewModel::skipWhatHappened,
      onPublish = viewModel::publish,
      onDismissError = viewModel::clearErrorMsg,
      onOpenExistingAlert = viewModel::openExistingAlert,
      onDismissAlreadyMissing = viewModel::dismissAlreadyMissing,
  )
}
