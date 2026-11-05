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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.android.spotted.R
import com.android.spotted.model.Pet.Pet

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
}

@Composable
fun PetProfileScreen(viewModel: PetProfileViewModel, petId: String, modifier: Modifier = Modifier) {
  LaunchedEffect(petId) { viewModel.loadPet(petId) }

  val state by viewModel.uiState.collectAsState()

  PetProfileContent(state = state, modifier = modifier)
}

@Composable
fun PetProfileContent(state: PetProfileUiState, modifier: Modifier = Modifier) {
  var isPhotoViewerOpen by remember { mutableStateOf(false) }
  LaunchedEffect(state.pet?.id, state.pet?.photoUrl) { isPhotoViewerOpen = false }

  Column(
      modifier = modifier.fillMaxSize().padding(16.dp),
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
      state.pet != null ->
          PetDetails(
              pet = state.pet,
              onPhotoClick = { if (!isPhotoViewerOpen) isPhotoViewerOpen = true },
          )
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
                  .background(Color.Black)
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
            color = Color.White,
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
