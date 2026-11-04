// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.components

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

internal data class PhotoPickerActions(
    val pickFromGallery: () -> Unit,
    val takePhoto: () -> Unit,
)

/** Shared gallery and camera launchers used by pet, profile, and sighting photos. */
@Composable
internal fun rememberPhotoPickerActions(
    onPhotoSelected: (Uri?) -> Unit,
    onCameraUnavailable: () -> Unit,
): PhotoPickerActions {
  val context = LocalContext.current
  val currentOnPhotoSelected by rememberUpdatedState(onPhotoSelected)
  val galleryLauncher =
      rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        // A null result means the user closed the picker, so keep the current photo.
        if (uri != null) currentOnPhotoSelected(uri)
      }

  // The camera is in front of our app; save this URI across rotation or process death so we can
  // identify the file the camera writes when our activity returns.
  var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
  val cameraLauncher =
      rememberLauncherForActivityResult(TakePicture()) { saved ->
        val uri = pendingCameraUri ?: return@rememberLauncherForActivityResult
        pendingCameraUri = null
        if (saved) {
          currentOnPhotoSelected(uri)
        } else {
          // A cancelled capture leaves an empty cache file behind unless we remove it.
          context.contentResolver.delete(uri, null, null)
        }
      }

  return PhotoPickerActions(
      pickFromGallery = {
        galleryLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
      },
      takePhoto = {
        val uri = CameraPhotoFile.newPhotoUri(context)
        pendingCameraUri = uri
        try {
          cameraLauncher.launch(uri)
        } catch (e: ActivityNotFoundException) {
          pendingCameraUri = null
          // No camera app can write this newly created file, so delete it instead of leaking it.
          context.contentResolver.delete(uri, null, null)
          onCameraUnavailable()
        }
      },
  )
}
