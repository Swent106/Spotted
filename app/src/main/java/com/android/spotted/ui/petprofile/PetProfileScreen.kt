// Portions of this code were generated with the help of Claude (Anthropic).
// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.petprofile

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
  const val BACK_BUTTON = "petProfile_backButton"
  const val EDIT_BUTTON = "petProfile_editButton"
}

/**
 * The profile of one of the user's pets (Figma "21 · Pet profile"), wired to [viewModel]. Calls
 * [onEdit] with the pet's id to open the edit form.
 */
@Composable
fun PetProfileScreen(
    viewModel: PetProfileViewModel,
    petId: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onEdit: (String) -> Unit = {},
) {
  // Loaded on every resume (e.g. back from the edit form, so the changes are shown) and when
  // petId changes while the screen stays resumed
  LifecycleResumeEffect(petId) {
    viewModel.loadPet(petId)
    onPauseOrDispose {}
  }

  val state by viewModel.uiState.collectAsStateWithLifecycle()

  PetProfileContent(
      state = state,
      modifier = modifier,
      onBack = onBack,
      onEdit = { state.pet?.id?.let(onEdit) },
  )
}

/** Stateless pet profile: the pet, then "Edit" pinned at the bottom. */
@Composable
fun PetProfileContent(
    state: PetProfileUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onEdit: () -> Unit = {},
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
      state.pet?.let { pet -> PetProfileFooter(pet.name, onEdit) }
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
private fun PetProfileFooter(name: String, onEdit: () -> Unit) {
  Spacer(Modifier.height(12.dp))
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
}
