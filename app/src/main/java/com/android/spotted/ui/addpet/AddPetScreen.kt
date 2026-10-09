package com.android.spotted.ui.addpet

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.android.spotted.model.Pet.Species

@Composable
fun AddPetScreen(
    viewModel: AddPetViewModel,
    ownerId: String,
    onSaveSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
  var name by remember { mutableStateOf("") }
  var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

  val uiState by viewModel.uiState.collectAsState()

  val imagePickerLauncher =
      rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri?
        ->
        selectedImageUri = uri
      }

  LaunchedEffect(uiState.saveSuccess) {
    if (uiState.saveSuccess) {
      onSaveSuccess()
    }
  }

  Column(
      modifier = modifier.fillMaxSize().padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
  ) {
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Pet Name") },
        modifier = Modifier.fillMaxWidth().testTag("add_pet_name_input"),
    )

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = { imagePickerLauncher.launch("image/*") },
        modifier = Modifier.testTag("add_pet_photo_button"),
    ) {
      Text(if (selectedImageUri == null) "Select Photo" else "Photo Selected")
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (uiState.errorMessage != null) {
      Text(text = uiState.errorMessage!!, color = androidx.compose.ui.graphics.Color.Red)
      Spacer(modifier = Modifier.height(16.dp))
    }

    if (uiState.isSaving) {
      CircularProgressIndicator(modifier = Modifier.testTag("add_pet_loading"))
    } else {
      Button(
          onClick = {
            viewModel.savePet(
                ownerId = ownerId,
                name = name,
                species = Species.DOG, // Defaults for sample
                photoUri = selectedImageUri?.toString(),
            )
          },
          enabled = name.isNotBlank(),
          modifier = Modifier.testTag("add_pet_save_button"),
      ) {
        Text("Save Pet")
      }
    }
  }
}
