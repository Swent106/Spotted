// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.addpets

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.android.spotted.ui.theme.SpottedButtonBorder
import com.android.spotted.ui.theme.SpottedInputBackground
import com.android.spotted.ui.theme.jakarta

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

/**
 * Stateless part, tested directly: the system picker itself cannot be automated in UI tests.
 *
 * Follows the photo row of the Figma "03 · Add a pet" screen: the photo on the left, the actions on
 * the right.
 */
@Composable
fun PetPhotoPickerContent(
    photoUri: String?,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Row(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier = modifier.fillMaxWidth(),
  ) {
    Box(Modifier.size(84.dp).clip(RoundedCornerShape(18.dp)).background(SpottedInputBackground)) {
      if (photoUri != null) {
        AsyncImage(
            model = photoUri,
            contentDescription = "Pet photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(84.dp).testTag(PetPhotoPickerTestTags.PREVIEW),
        )
      }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.weight(1f),
    ) {
      if (photoUri == null) {
        PhotoButton("Upload a photo", PetPhotoPickerTestTags.ADD_BUTTON, onPickPhoto)
      } else {
        PhotoButton("Change photo", PetPhotoPickerTestTags.CHANGE_BUTTON, onPickPhoto)
        PhotoButton("Remove photo", PetPhotoPickerTestTags.REMOVE_BUTTON, onRemovePhoto)
      }
    }
  }
}

@Composable
private fun PhotoButton(text: String, testTag: String, onClick: () -> Unit) {
  val shape = RoundedCornerShape(14.dp)
  Box(
      contentAlignment = Alignment.Center,
      modifier =
          Modifier.fillMaxWidth()
              .clip(shape)
              .background(Color.White)
              .border(1.5.dp, SpottedButtonBorder, shape)
              .clickable(role = Role.Button, onClick = onClick)
              .padding(vertical = 12.dp)
              .testTag(testTag),
  ) {
    Text(text, style = jakarta(13, 16f, 700, 0xFF2C3340))
  }
}
