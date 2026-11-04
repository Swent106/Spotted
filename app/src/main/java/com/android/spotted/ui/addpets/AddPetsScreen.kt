// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.addpets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.spotted.R
import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.Species
import com.android.spotted.ui.theme.SpottedBlue
import com.android.spotted.ui.theme.SpottedBlueTint
import com.android.spotted.ui.theme.SpottedInk
import com.android.spotted.ui.theme.SpottedInputBackground
import com.android.spotted.ui.theme.SpottedScreenBackground
import com.android.spotted.ui.theme.jakarta

object AddPetsScreenTestTags {
  const val SCREEN = "addPets_screen"
  const val BACK_BUTTON = "addPets_backButton"
  const val SKIP_BUTTON = "addPets_skipButton"
  const val NAME_INPUT = "addPets_nameInput"
  const val DESCRIPTION_INPUT = "addPets_descriptionInput"
  const val AGE_INPUT = "addPets_ageInput"
  const val ADDED_PETS = "addPets_addedPets"
  const val ERROR_MESSAGE = "addPets_errorMessage"
  const val SAVE_AND_CONTINUE_BUTTON = "addPets_saveAndContinueButton"
  const val ADD_ANOTHER_BUTTON = "addPets_addAnotherButton"
  const val LOADER = "addPets_loader"

  fun speciesChip(species: Species) = "addPets_speciesChip_${species.name}"

  fun behaviorChip(behavior: Behavior) = "addPets_behaviorChip_${behavior.name}"
}

/** Resource label shown for each [Species] chip. */
internal fun Species.labelRes(): Int =
    when (this) {
      Species.DOG -> R.string.add_pets_species_dog
      Species.CAT -> R.string.add_pets_species_cat
      Species.OTHER -> R.string.add_pets_species_other
    }

/** Resource label shown for each [Behavior] chip, under "Characteristics". */
internal fun Behavior.labelRes(): Int =
    when (this) {
      Behavior.STRESSED_IN_CROWDS -> R.string.add_pets_behavior_stressed_in_crowds
      Behavior.DOESNT_LIKE_BEING_TOUCHED -> R.string.add_pets_behavior_doesnt_like_being_touched
      Behavior.REACTIVE_TO_OTHER_ANIMALS -> R.string.add_pets_behavior_reactive_to_other_animals
      Behavior.FEARFUL_OF_STRANGERS -> R.string.add_pets_behavior_fearful_of_strangers
    }

private val sectionLabelStyle = jakarta(11, 15f, 700, 0xFF6B7280)
private val fieldTextStyle = jakarta(14, 18f, 600, 0xFF14161A)
private val fieldPlaceholderStyle = jakarta(14, 18f, 500, 0xFF6B7280)

/**
 * Stateless "Add your pet" screen (Figma "03 · Add a pet").
 *
 * The form scrolls, and the footer stays pinned at the bottom: "Save & continue" saves the pet (if
 * the form was started) and ends the step, "+" saves it and clears the form for another pet. "Skip"
 * is always shown, so users without a pet are never blocked.
 */
@Composable
fun AddPetsContent(
    state: AddPetsUiState,
    onBackClick: () -> Unit,
    onSkipClick: () -> Unit,
    onNameChange: (String) -> Unit,
    onSpeciesSelect: (Species) -> Unit,
    onPhotoChange: (String?) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onAgeChange: (String) -> Unit,
    onBehaviorToggle: (Behavior) -> Unit,
    onSaveAndContinueClick: () -> Unit,
    onAddAnotherClick: () -> Unit,
    isOnboarding: Boolean = true,
    onPhotoError: () -> Unit = {},
) {
  Box(
      Modifier.fillMaxSize()
          .background(SpottedScreenBackground)
          .testTag(AddPetsScreenTestTags.SCREEN)
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
      Column(
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
      ) {
        AddPetsHeader(isSaving = state.isSaving, isOnboarding, onBackClick, onSkipClick)
        FormCard {
          SectionLabel(R.string.add_pets_name_section)
          FormField(
              value = state.name,
              onValueChange = onNameChange,
              placeholder = stringResource(R.string.add_pets_name_placeholder),
              testTag = AddPetsScreenTestTags.NAME_INPUT,
          )
          SectionLabel(R.string.add_pets_animal_section)
          ChipFlow {
            Species.entries.forEach { species ->
              FormChip(
                  text = stringResource(species.labelRes()),
                  selected = state.species == species,
                  role = Role.RadioButton,
                  testTag = AddPetsScreenTestTags.speciesChip(species),
                  onClick = { onSpeciesSelect(species) },
              )
            }
          }
        }
        FormCard {
          SectionLabel(R.string.add_pets_photo_section)
          PetPhotoPicker(
              photoUri = state.photoUri,
              onPhotoSelected = onPhotoChange,
              onPhotoError = onPhotoError,
          )
        }
        // The details card is tighter than the others in the Figma
        FormCard(spacing = 8.dp) {
          SectionLabel(R.string.add_pets_description_section)
          FormField(
              value = state.description,
              onValueChange = onDescriptionChange,
              placeholder = stringResource(R.string.add_pets_description_placeholder),
              testTag = AddPetsScreenTestTags.DESCRIPTION_INPUT,
              singleLine = false,
              minHeight = 52.dp,
          )
          SectionLabel(R.string.add_pets_age_section)
          FormField(
              value = state.age,
              onValueChange = onAgeChange,
              placeholder = stringResource(R.string.add_pets_age_placeholder),
              testTag = AddPetsScreenTestTags.AGE_INPUT,
              width = 150.dp,
          )
          SectionLabel(R.string.add_pets_characteristics_section)
          ChipFlow {
            Behavior.entries.forEach { behavior ->
              FormChip(
                  text = stringResource(behavior.labelRes()),
                  selected = behavior in state.behaviors,
                  role = Role.Checkbox,
                  testTag = AddPetsScreenTestTags.behaviorChip(behavior),
                  onClick = { onBehaviorToggle(behavior) },
              )
            }
          }
        }
      }
      // Above the buttons, outside the scroll, so the result of a tap is always visible
      Spacer(Modifier.height(12.dp))
      if (state.addedPets.isNotEmpty()) {
        Text(
            stringResource(R.string.add_pets_added_pets, state.addedPets.joinToString { it.name }),
            style = jakarta(13, 18f, 600, 0xFF5F6672, TextAlign.Center),
            modifier =
                Modifier.fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .testTag(AddPetsScreenTestTags.ADDED_PETS),
        )
      }
      (state.errorMessage ?: state.missingField)?.let { message ->
        Text(
            message.resolve(),
            style = jakarta(13, 18f, 600, 0xFFD93025, TextAlign.Center),
            modifier =
                Modifier.fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .testTag(AddPetsScreenTestTags.ERROR_MESSAGE),
        )
      }
      AddPetsFooter(state, onSaveAndContinueClick, onAddAnotherClick)
    }
  }
}

/**
 * Back, title and, during onboarding ([isOnboarding]), the step and "Skip". Opened from Home, Back
 * is enough to leave without adding a pet.
 */
@Composable
private fun AddPetsHeader(
    isSaving: Boolean,
    isOnboarding: Boolean,
    onBackClick: () -> Unit,
    onSkipClick: () -> Unit,
) {
  Row(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth(),
  ) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.size(40.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(
                    onClickLabel = stringResource(R.string.add_pets_back),
                    role = Role.Button,
                    onClick = onBackClick,
                )
                .testTag(AddPetsScreenTestTags.BACK_BUTTON),
    ) {
      Icon(
          painterResource(R.drawable.ic_arrow_back),
          contentDescription = stringResource(R.string.add_pets_back),
          tint = SpottedInk,
          modifier = Modifier.size(20.dp),
      )
    }
    Column(Modifier.weight(1f)) {
      Text(
          stringResource(R.string.add_pets_screen_title),
          style = jakarta(23, 31f, 800, 0xFF14161A),
      )
      if (isOnboarding) {
        Text(
            stringResource(R.string.add_pets_onboarding_step),
            style = jakarta(12, 16f, 500, 0xFF5F6672),
        )
      }
    }
    if (isOnboarding) {
      Text(
          stringResource(R.string.add_pets_skip),
          style = jakarta(14, 18f, 700, 0xFF1B5BD7),
          modifier =
              Modifier.clip(RoundedCornerShape(10.dp))
                  .clickable(enabled = !isSaving, role = Role.Button, onClick = onSkipClick)
                  .padding(horizontal = 8.dp, vertical = 6.dp)
                  .testTag(AddPetsScreenTestTags.SKIP_BUTTON),
      )
    }
  }
}

@Composable
private fun FormCard(spacing: Dp = 10.dp, content: @Composable () -> Unit) {
  Column(
      verticalArrangement = Arrangement.spacedBy(spacing),
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
private fun SectionLabel(resourceId: Int) {
  Text(stringResource(resourceId), style = sectionLabelStyle)
}

@Composable
private fun AddPetsMessage.resolve(): String =
    when (this) {
      is AddPetsMessage.Resource -> stringResource(id, *arguments.toTypedArray())
    }

@Composable
private fun ChipFlow(content: @Composable () -> Unit) {
  FlowRow(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.fillMaxWidth(),
  ) {
    content()
  }
}

@Composable
private fun FormChip(
    text: String,
    selected: Boolean,
    role: Role,
    testTag: String,
    onClick: () -> Unit,
) {
  val shape = RoundedCornerShape(100.dp)
  val selection =
      if (role == Role.Checkbox) {
        Modifier.toggleable(value = selected, role = role, onValueChange = { onClick() })
      } else {
        Modifier.selectable(selected = selected, role = role, onClick = onClick)
      }
  Text(
      text,
      style =
          if (selected) jakarta(13, 16f, 700, 0xFF1B5BD7) else jakarta(13, 16f, 600, 0xFF2C3340),
      modifier =
          Modifier.clip(shape)
              .background(if (selected) SpottedBlueTint else SpottedInputBackground)
              .then(if (selected) Modifier.border(1.5.dp, SpottedBlue, shape) else Modifier)
              .then(selection)
              .padding(horizontal = 12.dp, vertical = 7.dp)
              .testTag(testTag),
  )
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    testTag: String,
    singleLine: Boolean = true,
    minHeight: Dp = 42.dp,
    width: Dp? = null,
) {
  BasicTextField(
      value = value,
      onValueChange = onValueChange,
      singleLine = singleLine,
      textStyle = fieldTextStyle,
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
      modifier =
          (if (width == null) Modifier.fillMaxWidth() else Modifier.width(width)).testTag(testTag),
      decorationBox = { innerTextField ->
        Box(
            contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(min = minHeight)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SpottedInputBackground)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
          if (value.isEmpty()) Text(placeholder, style = fieldPlaceholderStyle)
          innerTextField()
        }
      },
  )
}

@Composable
private fun AddPetsFooter(
    state: AddPetsUiState,
    onSaveAndContinueClick: () -> Unit,
    onAddAnotherClick: () -> Unit,
) {
  Row(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth(),
  ) {
    Button(
        onClick = onSaveAndContinueClick,
        enabled = state.canFinish,
        shape = RoundedCornerShape(14.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = SpottedBlue,
                contentColor = Color.White,
                disabledContainerColor = SpottedBlue.copy(alpha = 0.4f),
                disabledContentColor = Color.White,
            ),
        modifier =
            Modifier.weight(1f)
                .height(52.dp)
                .testTag(AddPetsScreenTestTags.SAVE_AND_CONTINUE_BUTTON),
    ) {
      if (state.isSaving) {
        CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 2.dp,
            modifier = Modifier.size(20.dp).testTag(AddPetsScreenTestTags.LOADER),
        )
      } else {
        Text(
            stringResource(R.string.add_pets_save_and_continue),
            style = jakarta(15, 20f, 700, 0xFFFFFFFF),
        )
      }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.size(52.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(
                    2.dp,
                    SpottedBlue.copy(alpha = if (state.canSave) 1f else 0.4f),
                    CircleShape,
                )
                .clickable(
                    enabled = state.canSave,
                    onClickLabel = stringResource(R.string.add_pets_add_another),
                    role = Role.Button,
                    onClick = onAddAnotherClick,
                )
                .testTag(AddPetsScreenTestTags.ADD_ANOTHER_BUTTON),
    ) {
      Text(
          "+",
          style = jakarta(26, 28f, 800, if (state.canSave) 0xFF1B5BD7 else 0x661B5BD7),
      )
    }
  }
}

/**
 * Stateful entry point of the Add pets screen.
 *
 * Collects the [AddPetsViewModel] state, forwards user input to it, and calls [onDone] once the
 * step ends (after "Save & continue" or "Skip").
 */
@Composable
fun AddPetsScreen(
    viewModel: AddPetsViewModel = viewModel(factory = AddPetsViewModel.Factory),
    onBack: () -> Unit = {},
    onDone: () -> Unit = {},
    isOnboarding: Boolean = true,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  val currentOnDone by rememberUpdatedState(onDone)
  LaunchedEffect(state.isDone) {
    if (state.isDone) {
      viewModel.onDoneHandled()
      currentOnDone()
    }
  }

  AddPetsContent(
      state = state,
      isOnboarding = isOnboarding,
      onBackClick = onBack,
      onSkipClick = viewModel::skip,
      onNameChange = viewModel::onNameChanged,
      onSpeciesSelect = viewModel::onSpeciesChanged,
      onPhotoChange = viewModel::onPhotoChanged,
      onDescriptionChange = viewModel::onDescriptionChanged,
      onAgeChange = viewModel::onAgeChanged,
      onBehaviorToggle = viewModel::onBehaviorToggled,
      onSaveAndContinueClick = viewModel::finish,
      onAddAnotherClick = viewModel::savePet,
      onPhotoError = viewModel::onPhotoPickerError,
  )
}
