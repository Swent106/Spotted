// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.addpets

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.android.spotted.ui.theme.SpottedButtonBorder
import com.android.spotted.ui.theme.SpottedInputBackground
import com.android.spotted.ui.theme.jakarta

object PetPhotoPickerTestTags {
  const val ADD_BUTTON = "pet_photo_add_button"
  const val TAKE_PHOTO_BUTTON = "pet_photo_take_photo_button"
  const val PREVIEW = "pet_photo_preview"
  const val CHANGE_BUTTON = "pet_photo_change_button"
  const val REMOVE_BUTTON = "pet_photo_remove_button"
}

/**
 * Lets the user add an optional pet photo, either from the gallery or with the camera.
 * - Gallery: the system Photo Picker. No storage permission: the user explicitly chooses the photo.
 * - Camera: the device's camera app, through [TakePicture]. It writes the photo into a file we
 *   share with a FileProvider (see [CameraPhotoFile]). No camera permission is needed because the
 *   camera app takes the picture, not us.
 *
 * Both send the photo up as a URI string through [onPhotoSelected], so the ViewModel and the upload
 * don't care where the photo came from.
 *
 * Do not add the CAMERA permission to the manifest: if the app declares it without the user
 * granting it, launching the camera app throws a SecurityException.
 *
 * @param canTakePhoto whether to offer the camera; false on devices without a camera.
 */
@Composable
fun PetPhotoPicker(
    photoUri: String?,
    onPhotoSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    canTakePhoto: Boolean = rememberDeviceHasCamera(),
) {
  val context = LocalContext.current

  val galleryLauncher =
      rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        // uri is null when the user closes the picker without choosing: keep the current photo.
        if (uri != null) onPhotoSelected(uri.toString())
      }

  // File the camera app is writing to. Saved so it survives rotation, and the app being killed
  // while the camera app is in front.
  var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

  val cameraLauncher =
      rememberLauncherForActivityResult(TakePicture()) { saved ->
        val uri = pendingCameraUri ?: return@rememberLauncherForActivityResult
        pendingCameraUri = null
        if (saved) {
          onPhotoSelected(uri.toString())
        } else {
          // Cancelled: keep the current photo and delete the empty file.
          context.contentResolver.delete(uri, null, null)
        }
      }

  PetPhotoPickerContent(
      photoUri = photoUri,
      onPickPhoto = { galleryLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
      onTakePhoto = {
        val uri = CameraPhotoFile.newPhotoUri(context)
        pendingCameraUri = uri
        try {
          cameraLauncher.launch(uri)
        } catch (e: ActivityNotFoundException) {
          // No camera app installed: nothing happens, the gallery option still works.
          pendingCameraUri = null
          context.contentResolver.delete(uri, null, null)
        }
      },
      onRemovePhoto = { onPhotoSelected(null) },
      canTakePhoto = canTakePhoto,
      modifier = modifier,
  )
}

@Composable
private fun rememberDeviceHasCamera(): Boolean {
  val context = LocalContext.current
  return remember(context) { CameraPhotoFile.deviceHasCamera(context) }
}

/**
 * Stateless part, tested directly: the system picker and the camera app cannot be automated in UI
 * tests.
 *
 * Follows the photo row of the Figma "03 · Add a pet" screen: the photo on the left, the actions on
 * the right.
 */
@Composable
fun PetPhotoPickerContent(
    photoUri: String?,
    onPickPhoto: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    modifier: Modifier = Modifier,
    canTakePhoto: Boolean = true,
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
        if (canTakePhoto) {
          PhotoButton("Take a photo", PetPhotoPickerTestTags.TAKE_PHOTO_BUTTON, onTakePhoto)
        }
      } else {
        PhotoButton("Change photo", PetPhotoPickerTestTags.CHANGE_BUTTON, onPickPhoto)
        if (canTakePhoto) {
          PhotoButton("Take a photo", PetPhotoPickerTestTags.TAKE_PHOTO_BUTTON, onTakePhoto)
        }
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
