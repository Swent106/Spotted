// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.editpet

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.spotted.R
import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.Species
import com.android.spotted.ui.addpets.ChipFlow
import com.android.spotted.ui.addpets.FormCard
import com.android.spotted.ui.addpets.FormChip
import com.android.spotted.ui.addpets.FormField
import com.android.spotted.ui.addpets.PetPhotoPicker
import com.android.spotted.ui.addpets.SectionLabel
import com.android.spotted.ui.addpets.labelRes
import com.android.spotted.ui.theme.SpottedTheme
import com.android.spotted.ui.theme.jakarta

object EditPetScreenTestTags {
  const val SCREEN = "editPet_screen"
  const val BACK_BUTTON = "editPet_backButton"
  const val LOADING = "editPet_loading"
  const val NAME_FIELD = "editPet_nameField"
  const val DESCRIPTION_FIELD = "editPet_descriptionField"
  const val ALLERGIES_FIELD = "editPet_allergiesField"
  const val ERROR = "editPet_error"
  const val SAVE_BUTTON = "editPet_saveButton"

  fun speciesChip(species: Species) = "editPet_speciesChip_${species.name}"

  fun behaviorChip(behavior: Behavior) = "editPet_behaviorChip_${behavior.name}"
}

/**
 * Edit form of one pet (Figma "21b · Edit a pet"), wired to [EditPetViewModel]. Calls [onSaved]
 * once the changes are saved.
 */
@Composable
fun EditPetScreen(
    petId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditPetViewModel = viewModel(key = petId, factory = EditPetViewModel.factory(petId)),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val currentOnSaved by rememberUpdatedState(onSaved)
  // The ViewModel has no "saved" flag: once a requested save is over, no error means it succeeded
  var saveRequested by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(saveRequested, state.saving, state.errorMessage) {
    if (saveRequested && !state.saving) {
      saveRequested = false
      if (state.errorMessage == null) currentOnSaved()
    }
  }

  EditPetContent(
      state = state,
      onBack = onBack,
      onNameChange = viewModel::onNameChanged,
      onSpeciesSelect = viewModel::onSpeciesChanged,
      onPhotoChange = viewModel::onPhotoChanged,
      onDescriptionChange = viewModel::onDescriptionChanged,
      onAllergiesChange = viewModel::onAllergiesChanged,
      onBehaviorToggle = viewModel::onBehaviorToggled,
      onSave = {
        if (state.canSave) {
          saveRequested = true
          viewModel.save()
        }
      },
  )
}

/**
 * Stateless edit form. It reuses the Add pet form pieces (cards, labels, fields, chips and the
 * photo picker with the camera), so both forms look and behave the same.
 */
@Composable
fun EditPetContent(
    state: EditPetUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onSpeciesSelect: (Species) -> Unit,
    onPhotoChange: (String?) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onAllergiesChange: (List<String>) -> Unit,
    onBehaviorToggle: (Behavior) -> Unit,
    onSave: () -> Unit,
) {
  Box(
      Modifier.fillMaxSize()
          .background(SpottedTheme.colors.screenBackground)
          .testTag(EditPetScreenTestTags.SCREEN)
  ) {
    Image(
        painterResource(R.drawable.background_textures),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = 0.4f,
        modifier = Modifier.fillMaxSize(),
    )
    if (state.isLoading) {
      CircularProgressIndicator(
          color = SpottedTheme.colors.blue,
          modifier = Modifier.align(Alignment.Center).testTag(EditPetScreenTestTags.LOADING),
      )
    } else if (state.errorMessage == R.string.edit_pet_load_error) {
      // Without the pet there is nothing to edit: no form, so nothing can look saved
      Column(
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.padding(start = 20.dp, top = 52.dp, end = 20.dp),
      ) {
        PetScreenHeader(
            title = "",
            subtitle = null,
            backTag = EditPetScreenTestTags.BACK_BUTTON,
            onBack = onBack,
        )
        ErrorText(state.errorMessage)
      }
    } else {
      EditPetForm(
          state,
          onBack,
          onNameChange,
          onSpeciesSelect,
          onPhotoChange,
          onDescriptionChange,
          onAllergiesChange,
          onBehaviorToggle,
          onSave,
      )
    }
  }
}

@Composable
private fun EditPetForm(
    state: EditPetUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onSpeciesSelect: (Species) -> Unit,
    onPhotoChange: (String?) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onAllergiesChange: (List<String>) -> Unit,
    onBehaviorToggle: (Behavior) -> Unit,
    onSave: () -> Unit,
) {
  // The ViewModel keeps the allergies as a list; the text typed is kept here, so a comma being
  // typed is not removed before the next allergy is written
  var allergiesText by rememberSaveable { mutableStateOf(state.allergies.joinToString(", ")) }

  Column(Modifier.fillMaxSize().padding(start = 20.dp, top = 52.dp, end = 20.dp, bottom = 34.dp)) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
    ) {
      PetScreenHeader(
          title = stringResource(R.string.edit_pet_title, state.name),
          subtitle = stringResource(R.string.edit_pet_subtitle, state.name),
          backTag = EditPetScreenTestTags.BACK_BUTTON,
          onBack = onBack,
      )
      FormCard {
        SectionLabel(R.string.add_pets_photo_section)
        PetPhotoPicker(
            photoUri = state.photoUri,
            onPhotoSelected = onPhotoChange,
            onPhotoError = {},
            isSaving = state.saving,
        )
        SectionLabel(R.string.add_pets_name_section)
        FormField(
            value = state.name,
            onValueChange = onNameChange,
            placeholder = stringResource(R.string.add_pets_name_placeholder),
            testTag = EditPetScreenTestTags.NAME_FIELD,
        )
      }
      FormCard {
        SectionLabel(R.string.add_pets_animal_section)
        ChipFlow {
          Species.entries.forEach { species ->
            FormChip(
                text = stringResource(species.labelRes()),
                selected = state.species == species,
                role = Role.RadioButton,
                testTag = EditPetScreenTestTags.speciesChip(species),
                onClick = { onSpeciesSelect(species) },
            )
          }
        }
      }
      FormCard(spacing = 8.dp) {
        SectionLabel(R.string.add_pets_description_section)
        FormField(
            value = state.description,
            onValueChange = onDescriptionChange,
            placeholder = stringResource(R.string.add_pets_description_placeholder),
            testTag = EditPetScreenTestTags.DESCRIPTION_FIELD,
            singleLine = false,
            minHeight = 52.dp,
        )
        SectionLabel(R.string.edit_pet_allergies_section)
        FormField(
            value = allergiesText,
            onValueChange = { text ->
              allergiesText = text
              onAllergiesChange(text.split(",").map(String::trim).filter(String::isNotEmpty))
            },
            placeholder = stringResource(R.string.edit_pet_allergies_placeholder),
            testTag = EditPetScreenTestTags.ALLERGIES_FIELD,
        )
        SectionLabel(R.string.add_pets_characteristics_section)
        ChipFlow {
          Behavior.entries.forEach { behavior ->
            FormChip(
                text = stringResource(behavior.labelRes()),
                selected = behavior in state.behaviors,
                role = Role.Checkbox,
                testTag = EditPetScreenTestTags.behaviorChip(behavior),
                onClick = { onBehaviorToggle(behavior) },
            )
          }
        }
      }
    }
    Spacer(Modifier.height(12.dp))
    state.errorMessage?.let { ErrorText(it, Modifier.padding(bottom = 8.dp)) }
    Button(
        onClick = onSave,
        enabled = state.canSave,
        shape = RoundedCornerShape(14.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = SpottedTheme.colors.blue,
                contentColor = SpottedTheme.colors.onAccent,
                disabledContainerColor = SpottedTheme.colors.blue.copy(alpha = 0.4f),
                disabledContentColor = SpottedTheme.colors.onAccent,
            ),
        modifier = Modifier.fillMaxWidth().height(51.dp).testTag(EditPetScreenTestTags.SAVE_BUTTON),
    ) {
      if (state.saving) {
        CircularProgressIndicator(
            color = SpottedTheme.colors.onAccent,
            strokeWidth = 2.dp,
            modifier = Modifier.size(20.dp),
        )
      } else {
        Text(
            stringResource(R.string.edit_pet_save),
            style = jakarta(15, 20f, 700, SpottedTheme.colors.onAccent),
        )
      }
    }
  }
}

@Composable
private fun ErrorText(@StringRes error: Int, modifier: Modifier = Modifier) {
  Text(
      stringResource(error),
      style = jakarta(13, 18f, 600, SpottedTheme.colors.error, TextAlign.Center),
      modifier = modifier.fillMaxWidth().testTag(EditPetScreenTestTags.ERROR),
  )
}

/**
 * Header of the pet screens (Figma 21 and 21b): the round Back button, then [title] and, if any,
 * [subtitle].
 */
@Composable
internal fun PetScreenHeader(
    title: String,
    subtitle: String?,
    backTag: String,
    onBack: () -> Unit,
) {
  Row(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth(),
  ) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.size(48.dp)
                .clip(CircleShape)
                .background(SpottedTheme.colors.surface)
                .clickable(role = Role.Button, onClick = onBack)
                .testTag(backTag),
    ) {
      Icon(
          painterResource(R.drawable.ic_arrow_back),
          contentDescription = stringResource(R.string.add_pets_back),
          tint = SpottedTheme.colors.ink,
          modifier = Modifier.size(22.dp),
      )
    }
    Column(Modifier.weight(1f)) {
      Text(title, style = jakarta(23, 31f, 800, SpottedTheme.colors.ink))
      subtitle?.let { Text(it, style = jakarta(12, 16f, 500, SpottedTheme.colors.textSecondary)) }
    }
  }
}
