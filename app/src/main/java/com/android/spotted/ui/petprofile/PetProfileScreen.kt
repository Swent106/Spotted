// Portions of this code were generated with the help of Claude (Anthropic).
// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.petprofile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.android.spotted.R
import com.android.spotted.model.Pet.Pet
import com.android.spotted.ui.components.SpottedTopBar
import com.android.spotted.ui.theme.SpottedIllustration
import com.android.spotted.ui.theme.SpottedTheme

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
}

@Composable
fun PetProfileScreen(
    viewModel: PetProfileViewModel,
    petId: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
  LaunchedEffect(petId) { viewModel.loadPet(petId) }

  val state by viewModel.uiState.collectAsState()

  PetProfileContent(state = state, modifier = modifier, onBack = onBack)
}

/**
 * The pet's photo, name and species; with [onBack], a top bar to go back to the previous screen.
 */
@Composable
fun PetProfileContent(
    state: PetProfileUiState,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
  var isPhotoViewerOpen by remember { mutableStateOf(false) }
  LaunchedEffect(state.pet?.id, state.pet?.photoUrl) { isPhotoViewerOpen = false }

  Column(modifier.fillMaxSize().padding(16.dp)) {
    onBack?.let {
      SpottedTopBar(
          title = state.pet?.name.orEmpty(),
          onBack = it,
          backTestTag = PetProfileScreenTestTags.BACK_BUTTON,
      )
    }
    PetProfileBody(state, onPhotoClick = { if (!isPhotoViewerOpen) isPhotoViewerOpen = true })
  }

  val photoUrl = state.pet?.photoUrl
  if (isPhotoViewerOpen && photoUrl != null) {
    PhotoViewer(photoUrl, onClose = { isPhotoViewerOpen = false })
  }
}

@Composable
private fun ColumnScope.PetProfileBody(state: PetProfileUiState, onPhotoClick: () -> Unit) {
  Column(
      modifier = Modifier.weight(1f).fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
  ) {
    when {
      state.isLoading ->
          CircularProgressIndicator(modifier = Modifier.testTag(PetProfileScreenTestTags.LOADING))
      state.errorMessage != null ->
          Text(
              text = state.errorMessage,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.testTag(PetProfileScreenTestTags.ERROR),
          )
      state.pet != null -> PetDetails(pet = state.pet, onPhotoClick = onPhotoClick)
    }
  }
}

/** The pet's photo full screen, closed with "Close" or Back. */
@Composable
private fun PhotoViewer(photoUrl: String, onClose: () -> Unit) {
  Dialog(
      onDismissRequest = onClose,
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
                  .clickable(onClick = onClose)
                  .testTag(PetProfileScreenTestTags.PHOTO_VIEWER_CLOSE),
      )
    }
  }
}

@Composable
private fun PetDetails(pet: Pet, onPhotoClick: () -> Unit) {
  if (pet.photoUrl != null) {
    AsyncImage(
        model = pet.photoUrl,
        contentDescription = stringResource(R.string.pet_profile_photo_description),
        modifier =
            Modifier.size(120.dp)
                .clip(CircleShape)
                .clickable(onClick = onPhotoClick)
                .testTag(PetProfileScreenTestTags.IMAGE),
        contentScale = ContentScale.Crop,
    )
  } else {
    Box(
        modifier =
            Modifier.size(120.dp)
                .clip(CircleShape)
                .testTag(PetProfileScreenTestTags.IMAGE_PLACEHOLDER),
        contentAlignment = Alignment.Center,
    ) {
      Text(stringResource(R.string.pet_profile_no_photo))
    }
  }

  Spacer(modifier = Modifier.height(16.dp))

  Text(
      text = pet.name,
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier.testTag(PetProfileScreenTestTags.NAME),
  )

  Text(
      text = stringResource(R.string.pet_profile_species, pet.species.name),
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.testTag(PetProfileScreenTestTags.SPECIES),
  )
}
