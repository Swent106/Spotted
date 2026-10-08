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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun PetProfileScreen(viewModel: PetProfileViewModel, petId: String, modifier: Modifier = Modifier) {
  LaunchedEffect(petId) { viewModel.loadPet(petId) }

  val pet by viewModel.pet.collectAsState()

  Column(
      modifier = modifier.fillMaxSize().padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
  ) {
    if (pet == null) {
      CircularProgressIndicator(modifier = Modifier.testTag("pet_profile_loading"))
    } else {
      val loadedPet = pet!!

      if (loadedPet.photoUrl != null) {
        AsyncImage(
            model = loadedPet.photoUrl,
            contentDescription = "Pet Profile Picture",
            modifier = Modifier.size(120.dp).clip(CircleShape).testTag("pet_profile_image"),
            contentScale = ContentScale.Crop,
        )
      } else {
        Box(
            modifier =
                Modifier.size(120.dp).clip(CircleShape).testTag("pet_profile_image_placeholder"),
            contentAlignment = Alignment.Center,
        ) {
          Text("No Photo")
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
          text = loadedPet.name,
          style = MaterialTheme.typography.headlineMedium,
          modifier = Modifier.testTag("pet_profile_name"),
      )

      Text(
          text = "Species: ${loadedPet.species.name}",
          style = MaterialTheme.typography.bodyLarge,
      )
    }
  }
}
