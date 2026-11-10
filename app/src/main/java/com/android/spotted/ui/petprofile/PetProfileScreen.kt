// Portions of this code were generated with the help of Claude (Anthropic).
// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.petprofile

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.android.spotted.R
import com.android.spotted.model.Pet.Pet
import com.android.spotted.ui.addpets.ChipFlow
import com.android.spotted.ui.addpets.FormCard
import com.android.spotted.ui.addpets.SectionLabel
import com.android.spotted.ui.addpets.labelRes
import com.android.spotted.ui.editpet.EditPetViewModel
import com.android.spotted.ui.editpet.PetScreenHeader
import com.android.spotted.ui.theme.LightSpottedColors
import com.android.spotted.ui.theme.SpottedIllustration
import com.android.spotted.ui.theme.SpottedTheme
import com.android.spotted.ui.theme.jakarta

object PetProfileScreenTestTags {
  const val IMAGE = "pet_profile_image"
  const val IMAGE_PLACEHOLDER = "pet_profile_image_placeholder"
  const val PHOTO_VIEWER = "pet_profile_photo_viewer"
  const val PHOTO_VIEWER_IMAGE = "pet_profile_photo_viewer_image"
  const val PHOTO_VIEWER_CLOSE = "pet_profile_photo_viewer_close"
  const val NAME = "pet_profile_name"
  const val SPECIES = "pet_profile_species"
  const val LOADING = "pet_profile_loading"
  const val ERROR = "pet_profile_error"
  const val BACK_BUTTON = "pet_profile_back_button"
  const val EDIT_BUTTON = "petProfile_editButton"
  const val DELETE_BUTTON = "petProfile_deleteButton"
  const val DELETE_ERROR = "petProfile_deleteError"
  const val DELETE_DIALOG = "petProfile_deleteDialog"
  const val DELETE_CONFIRM = "petProfile_deleteConfirm"
  const val DELETE_CANCEL = "petProfile_deleteCancel"
  const val ALERT_OPEN_DIALOG = "petProfile_alertOpenDialog"
  const val ALERT_OPEN_OK = "petProfile_alertOpenOk"
}

/**
 * The profile of one of the user's pets (Figma "21 · Pet profile"), wired to its ViewModels:
 * [viewModel] shows the pet and [deleteViewModel] deletes it. Calls [onEdit] with the pet's id to
 * open the edit form, and [onDeleted] once the pet is deleted.
 */
@Composable
fun PetProfileScreen(
    viewModel: PetProfileViewModel,
    petId: String,
    deleteViewModel: EditPetViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onEdit: (String) -> Unit = {},
    onDeleted: () -> Unit = {},
) {
  // Loaded on every resume (e.g. back from the edit form, so the changes are shown) and when
  // petId changes while the screen stays resumed
  LifecycleResumeEffect(petId) {
    viewModel.loadPet(petId)
    onPauseOrDispose {}
  }

  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val deleteState by deleteViewModel.uiState.collectAsStateWithLifecycle()
  val currentOnDeleted by rememberUpdatedState(onDeleted)
  var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
  var showRefusedDialog by rememberSaveable { mutableStateOf(false) }
  var deleteRequested by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(deleteState.deleted) { if (deleteState.deleted) currentOnDeleted() }
  // Once a requested delete is over, the open alert error means it was refused
  LaunchedEffect(deleteRequested, deleteState.saving, deleteState.errorMessage) {
    if (deleteRequested && !deleteState.saving) {
      deleteRequested = false
      showRefusedDialog = deleteState.errorMessage == R.string.edit_pet_open_alert_error
    }
  }

  PetProfileContent(
      state = state,
      modifier = modifier,
      showDeleteDialog = showDeleteDialog,
      showRefusedDialog = showRefusedDialog,
      canDelete = !deleteState.isLoading,
      isDeleting = deleteState.saving,
      // The open alert error has its own dialog; the others would leave Delete doing nothing
      deleteError =
          deleteState.errorMessage?.takeIf {
            it == R.string.edit_pet_delete_error || it == R.string.edit_pet_load_error
          },
      onBack = onBack,
      onEdit = { state.pet?.id?.let(onEdit) },
      onDelete = { showDeleteDialog = true },
      onConfirmDelete = {
        showDeleteDialog = false
        deleteRequested = true
        deleteViewModel.delete()
      },
      onCancelDelete = { showDeleteDialog = false },
      onDismissRefused = { showRefusedDialog = false },
  )
}

/**
 * Stateless pet profile. "Delete" opens a confirmation ([showDeleteDialog]); when the pet still has
 * an open alert, [showRefusedDialog] explains why it can't be deleted.
 */
@Composable
fun PetProfileContent(
    state: PetProfileUiState,
    modifier: Modifier = Modifier,
    showDeleteDialog: Boolean = false,
    showRefusedDialog: Boolean = false,
    canDelete: Boolean = true,
    isDeleting: Boolean = false,
    @StringRes deleteError: Int? = null,
    onBack: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onCancelDelete: () -> Unit = {},
    onDismissRefused: () -> Unit = {},
) {
  var isPhotoViewerOpen by remember { mutableStateOf(false) }
  LaunchedEffect(state.pet?.id, state.pet?.photoUrl) { isPhotoViewerOpen = false }

  Box(modifier.fillMaxSize().background(SpottedTheme.colors.screenBackground)) {
    TexturedBackground()
    Column(
        Modifier.fillMaxSize().padding(start = 20.dp, top = 52.dp, end = 20.dp, bottom = 34.dp)
    ) {
      Column(
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
      ) {
        // A fixed title: the name is already in the card, and is not known while loading
        PetScreenHeader(
            title = stringResource(R.string.pet_profile_title),
            subtitle = stringResource(R.string.pet_profile_subtitle),
            backTag = PetProfileScreenTestTags.BACK_BUTTON,
            onBack = onBack,
        )
        // The pet stays shown while it is loaded again (e.g. back from the edit form)
        when {
          state.pet != null ->
              PetDetails(
                  pet = state.pet,
                  onPhotoClick = { if (!isPhotoViewerOpen) isPhotoViewerOpen = true },
              )
          state.isLoading ->
              CircularProgressIndicator(
                  color = SpottedTheme.colors.blue,
                  modifier =
                      Modifier.align(Alignment.CenterHorizontally)
                          .testTag(PetProfileScreenTestTags.LOADING),
              )
          state.errorMessage != null ->
              Text(
                  text = state.errorMessage,
                  style = jakarta(13, 18f, 600, SpottedTheme.colors.error),
                  modifier = Modifier.testTag(PetProfileScreenTestTags.ERROR),
              )
        }
      }
      state.pet?.let { pet ->
        PetProfileFooter(pet.name, canDelete, isDeleting, deleteError, onEdit, onDelete)
      }
    }
  }

  val photoUrl = state.pet?.photoUrl
  if (isPhotoViewerOpen && photoUrl != null) {
    Dialog(
        onDismissRequest = { isPhotoViewerOpen = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
      Box(
          modifier =
              Modifier.fillMaxSize()
                  .background(SpottedIllustration.cameraBackground)
                  .testTag(PetProfileScreenTestTags.PHOTO_VIEWER),
          contentAlignment = Alignment.Center,
      ) {
        AsyncImage(
            model = photoUrl,
            contentDescription = stringResource(R.string.pet_profile_photo_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().testTag(PetProfileScreenTestTags.PHOTO_VIEWER_IMAGE),
        )
        Text(
            text = "Close",
            color = SpottedTheme.colors.onAccent,
            modifier =
                Modifier.align(Alignment.TopEnd)
                    .padding(24.dp)
                    .clickable { isPhotoViewerOpen = false }
                    .testTag(PetProfileScreenTestTags.PHOTO_VIEWER_CLOSE),
        )
      }
    }
  }

  val name = state.pet?.name ?: return
  if (showDeleteDialog) {
    PetProfileDialog(
        title = stringResource(R.string.pet_profile_delete_title, name),
        message = stringResource(R.string.pet_profile_delete_message, name),
        onDismiss = onCancelDelete,
        testTag = PetProfileScreenTestTags.DELETE_DIALOG,
    ) {
      DialogButton(
          text = stringResource(R.string.pet_profile_delete, name),
          containerColor = SpottedTheme.colors.error,
          contentColor = SpottedTheme.colors.onAccent,
          testTag = PetProfileScreenTestTags.DELETE_CONFIRM,
          onClick = onConfirmDelete,
      )
      DialogButton(
          text = stringResource(R.string.pet_profile_keep, name),
          containerColor = SpottedTheme.colors.inputBackground,
          contentColor = SpottedTheme.colors.ink,
          testTag = PetProfileScreenTestTags.DELETE_CANCEL,
          onClick = onCancelDelete,
      )
    }
  }
  if (showRefusedDialog) {
    PetProfileDialog(
        title = stringResource(R.string.pet_profile_refused_title, name),
        message = stringResource(R.string.pet_profile_refused_message, name),
        onDismiss = onDismissRefused,
        testTag = PetProfileScreenTestTags.ALERT_OPEN_DIALOG,
    ) {
      DialogButton(
          text = stringResource(R.string.pet_profile_ok),
          containerColor = SpottedTheme.colors.inputBackground,
          contentColor = SpottedTheme.colors.ink,
          testTag = PetProfileScreenTestTags.ALERT_OPEN_OK,
          onClick = onDismissRefused,
      )
    }
  }
}

/** The app's paw-print texture, faded over the screen background. */
@Composable
private fun TexturedBackground() {
  Image(
      painterResource(R.drawable.background_textures),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      alpha = 0.4f,
      modifier = Modifier.fillMaxSize(),
  )
}

@Composable
private fun PetDetails(pet: Pet, onPhotoClick: () -> Unit) {
  FormCard(spacing = 12.dp) {
    val photoModifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(18.dp))
    if (pet.photoUrl != null) {
      AsyncImage(
          model = pet.photoUrl,
          contentDescription = stringResource(R.string.pet_profile_photo_description),
          modifier =
              photoModifier
                  .background(SpottedIllustration.bubbleBeige)
                  .clickable(
                      onClickLabel = stringResource(R.string.pet_profile_view_photo),
                      onClick = onPhotoClick,
                  )
                  .testTag(PetProfileScreenTestTags.IMAGE),
          contentScale = ContentScale.Crop,
      )
    } else {
      Box(
          modifier =
              photoModifier
                  .background(SpottedIllustration.bubbleBeige)
                  .testTag(PetProfileScreenTestTags.IMAGE_PLACEHOLDER),
          contentAlignment = Alignment.Center,
      ) {
        // On the beige illustration colour, the same in both modes, so the text stays dark too
        Text(
            stringResource(R.string.pet_profile_no_photo),
            style = jakarta(12, 15f, 600, LightSpottedColors.textSecondary),
        )
      }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            pet.name,
            style = jakarta(20, 25f, 800, SpottedTheme.colors.ink),
            modifier = Modifier.testTag(PetProfileScreenTestTags.NAME),
        )
        Text(
            stringResource(pet.species.labelRes()),
            style = jakarta(13, 16f, 600, SpottedTheme.colors.inkSoft),
            modifier =
                Modifier.clip(RoundedCornerShape(100.dp))
                    .background(SpottedTheme.colors.inputBackground)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag(PetProfileScreenTestTags.SPECIES),
        )
      }
      if (pet.note.isNotBlank()) {
        Text(pet.note, style = jakarta(13, 16f, 500, SpottedTheme.colors.textSecondary))
      }
    }
  }
  if (pet.behaviors.isNotEmpty() || pet.allergies.isNotEmpty()) {
    FormCard(spacing = 8.dp) {
      if (pet.behaviors.isNotEmpty()) {
        SectionLabel(R.string.add_pets_characteristics_section)
        ChipFlow {
          pet.behaviors.forEach { behavior ->
            val shape = RoundedCornerShape(100.dp)
            Text(
                stringResource(behavior.labelRes()),
                style = jakarta(13, 16f, 700, SpottedTheme.colors.blue),
                modifier =
                    Modifier.clip(shape)
                        .background(SpottedTheme.colors.blueTint)
                        .border(1.5.dp, SpottedTheme.colors.blue, shape)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
            )
          }
        }
      }
      if (pet.allergies.isNotEmpty()) {
        if (pet.behaviors.isNotEmpty()) Spacer(Modifier.height(4.dp))
        SectionLabel(R.string.edit_pet_allergies_section)
        Text(
            pet.allergies.joinToString(", "),
            style = jakarta(14, 18f, 700, SpottedTheme.colors.ink),
        )
      }
    }
  }
}

@Composable
private fun PetProfileFooter(
    name: String,
    canDelete: Boolean,
    isDeleting: Boolean,
    @StringRes deleteError: Int?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
  Spacer(Modifier.height(12.dp))
  deleteError?.let { error ->
    Text(
        stringResource(error),
        style = jakarta(13, 18f, 600, SpottedTheme.colors.error, TextAlign.Center),
        modifier =
            Modifier.fillMaxWidth()
                .padding(bottom = 8.dp)
                .testTag(PetProfileScreenTestTags.DELETE_ERROR),
    )
  }
  Button(
      onClick = onEdit,
      shape = RoundedCornerShape(14.dp),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = SpottedTheme.colors.blue,
              contentColor = SpottedTheme.colors.onAccent,
          ),
      modifier =
          Modifier.fillMaxWidth().height(49.dp).testTag(PetProfileScreenTestTags.EDIT_BUTTON),
  ) {
    Text(
        stringResource(R.string.pet_profile_edit, name),
        style = jakarta(15, 20f, 700, SpottedTheme.colors.onAccent),
    )
  }
  Spacer(Modifier.height(10.dp))
  val shape = RoundedCornerShape(14.dp)
  Box(
      contentAlignment = Alignment.Center,
      modifier =
          Modifier.fillMaxWidth()
              .height(51.dp)
              .clip(shape)
              .background(SpottedTheme.colors.surface)
              .border(1.dp, SpottedTheme.colors.error, shape)
              .clickable(enabled = canDelete && !isDeleting, role = Role.Button, onClick = onDelete)
              .testTag(PetProfileScreenTestTags.DELETE_BUTTON),
  ) {
    if (isDeleting) {
      CircularProgressIndicator(
          color = SpottedTheme.colors.error,
          strokeWidth = 2.dp,
          modifier = Modifier.size(20.dp),
      )
    } else {
      Text(
          stringResource(R.string.pet_profile_delete, name),
          style = jakarta(15, 20f, 700, SpottedTheme.colors.error),
      )
    }
  }
}

/** A centred card over the screen (Figma 21c and 21d): title, message, then stacked buttons. */
@Composable
private fun PetProfileDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    testTag: String,
    buttons: @Composable () -> Unit,
) {
  // Figma: 330 dp wide on a 390 dp screen, so 30 dp from each side
  Dialog(
      onDismissRequest = onDismiss,
      properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier.padding(horizontal = 30.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(SpottedTheme.colors.surface)
                .padding(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 18.dp)
                .testTag(testTag),
    ) {
      Text(title, style = jakarta(20, 25f, 800, SpottedTheme.colors.ink))
      Text(message, style = jakarta(13, 18.2f, 500, SpottedTheme.colors.textSecondary))
      buttons()
    }
  }
}

@Composable
private fun DialogButton(
    text: String,
    containerColor: Color,
    contentColor: Color,
    testTag: String,
    onClick: () -> Unit,
) {
  Button(
      onClick = onClick,
      shape = RoundedCornerShape(14.dp),
      colors =
          ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
      modifier = Modifier.fillMaxWidth().height(49.dp).testTag(testTag),
  ) {
    Text(text, style = jakarta(15, 20f, 700, contentColor))
  }
}
