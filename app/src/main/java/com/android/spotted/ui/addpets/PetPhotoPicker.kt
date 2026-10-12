package com.android.spotted.ui.addpets

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

object PetPhotoPickerTestTags {
  const val ADD_BUTTON = "pet_photo_add_button"
  const val PREVIEW = "pet_photo_preview"
  const val CHANGE_BUTTON = "pet_photo_change_button"
  const val REMOVE_BUTTON = "pet_photo_remove_button"
}

/**
 * Lets the user pick an optional pet photo with the system Photo Picker.
 *
 * The Photo Picker needs no storage permission: the user explicitly chooses the photo. The selected
 * photo is passed up as a URI string; the ViewModel stores it and uploads it on save.
 */
@Composable
fun PetPhotoPicker(
    photoUri: String?,
    onPhotoSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
  val launcher =
      rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        // uri is null when the user closes the picker without choosing: keep the current photo.
        if (uri != null) onPhotoSelected(uri.toString())
      }

  PetPhotoPickerContent(
      photoUri = photoUri,
      onPickPhoto = { launcher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
      onRemovePhoto = { onPhotoSelected(null) },
      modifier = modifier,
  )
}

/** Stateless part, tested directly: the system picker itself cannot be automated in UI tests. */
@Composable
fun PetPhotoPickerContent(
    photoUri: String?,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier = modifier,
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (photoUri == null) {
      OutlinedButton(
          onClick = onPickPhoto,
          modifier = Modifier.testTag(PetPhotoPickerTestTags.ADD_BUTTON),
      ) {
        Text("Add a photo (optional)")
      }
    } else {
      AsyncImage(
          model = photoUri,
          contentDescription = "Pet photo",
          contentScale = ContentScale.Crop,
          modifier =
              Modifier.size(120.dp).clip(CircleShape).testTag(PetPhotoPickerTestTags.PREVIEW),
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            onClick = onPickPhoto,
            modifier = Modifier.testTag(PetPhotoPickerTestTags.CHANGE_BUTTON),
        ) {
          Text("Change photo")
        }
        TextButton(
            onClick = onRemovePhoto,
            modifier = Modifier.testTag(PetPhotoPickerTestTags.REMOVE_BUTTON),
        ) {
          Text("Remove photo")
        }
      }
    }
  }
}
