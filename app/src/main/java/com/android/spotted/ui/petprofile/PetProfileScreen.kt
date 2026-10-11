package com.android.spotted.ui.petprofile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.android.spotted.R
import com.android.spotted.model.Pet.Pet

@Composable
fun PetProfileScreen(viewModel: PetProfileViewModel, petId: String, modifier: Modifier = Modifier) {
  LaunchedEffect(petId) { viewModel.loadPet(petId) }

  val state by viewModel.uiState.collectAsState()

  PetProfileContent(state = state, modifier = modifier)
}

@Composable
fun PetProfileContent(state: PetProfileUiState, modifier: Modifier = Modifier) {
  Column(
      modifier = modifier.fillMaxSize().padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
  ) {
    when {
      state.isLoading ->
          CircularProgressIndicator(modifier = Modifier.testTag("pet_profile_loading"))
      state.errorMessage != null ->
          Text(
              text = state.errorMessage,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.testTag("pet_profile_error"),
          )
      state.pet != null -> PetDetails(state.pet)
    }
  }
}

@Composable
private fun PetDetails(pet: Pet) {
  if (pet.photoUrl != null) {
    AsyncImage(
        model = pet.photoUrl,
        contentDescription = stringResource(R.string.pet_profile_photo_description),
        modifier = Modifier.size(120.dp).clip(CircleShape).testTag("pet_profile_image"),
        contentScale = ContentScale.Crop,
    )
  } else {
    Box(
        modifier = Modifier.size(120.dp).clip(CircleShape).testTag("pet_profile_image_placeholder"),
        contentAlignment = Alignment.Center,
    ) {
      Text(stringResource(R.string.pet_profile_no_photo))
    }
  }

  Spacer(modifier = Modifier.height(16.dp))

  Text(
      text = pet.name,
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier.testTag("pet_profile_name"),
  )

  Text(
      text = stringResource(R.string.pet_profile_species, pet.species.name),
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.testTag("pet_profile_species"),
  )
}
