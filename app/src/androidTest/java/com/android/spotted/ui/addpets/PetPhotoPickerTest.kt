package com.android.spotted.ui.addpets

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PetPhotoPickerTest {

  @get:Rule val composeRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  // ---------- Stateless content: rendering and callbacks ----------

  private var pickCalls = 0
  private var takeCalls = 0
  private var removeCalls = 0

  private fun setContent(photoUri: String?, canTakePhoto: Boolean = true) {
    composeRule.setContent {
      PetPhotoPickerContent(
        photoUri = photoUri,
        onPickPhoto = { pickCalls++ },
        onTakePhoto = { takeCalls++ },
        onRemovePhoto = { removeCalls++ },
        canTakePhoto = canTakePhoto,
      )
    }
  }

  @Test
  fun withoutPhoto_showsAddAndTakeButtonsOnly() {
    setContent(photoUri = null)

    composeRule.onNodeWithTag(PetPhotoPickerTestTags.ADD_BUTTON).assertIsDisplayed()
    composeRule.onNodeWithTag(PetPhotoPickerTestTags.TAKE_PHOTO_BUTTON).assertIsDisplayed()
    composeRule.onNodeWithTag(PetPhotoPickerTestTags.PREVIEW).assertDoesNotExist()
    composeRule.onNodeWithTag(PetPhotoPickerTestTags.REMOVE_BUTTON).assertDoesNotExist()
  }

  @Test
  fun addButton_requestsThePicker() {
    setContent(photoUri = null)

    composeRule.onNodeWithTag(PetPhotoPickerTestTags.ADD_BUTTON).performClick()

    assertEquals(1, pickCalls)
    assertEquals(0, takeCalls)
  }

  @Test
  fun takePhotoButton_requestsTheCamera() {
    setContent(photoUri = null)

    composeRule.onNodeWithTag(PetPhotoPickerTestTags.TAKE_PHOTO_BUTTON).performClick()

    assertEquals(1, takeCalls)
    assertEquals(0, pickCalls)
  }

  @Test
  fun withoutCamera_takePhotoButtonIsHidden() {
    setContent(photoUri = null, canTakePhoto = false)

    composeRule.onNodeWithTag(PetPhotoPickerTestTags.ADD_BUTTON).assertIsDisplayed()
    composeRule.onNodeWithTag(PetPhotoPickerTestTags.TAKE_PHOTO_BUTTON).assertDoesNotExist()
  }

  @Test
  fun withPhoto_showsPreviewAndActions() {
    setContent(photoUri = "content://test/photo.jpg")

    composeRule.onNodeWithTag(PetPhotoPickerTestTags.PREVIEW).assertIsDisplayed()
    composeRule.onNodeWithTag(PetPhotoPickerTestTags.CHANGE_BUTTON).assertIsDisplayed()
    composeRule.onNodeWithTag(PetPhotoPickerTestTags.TAKE_PHOTO_BUTTON).assertIsDisplayed()
    composeRule.onNodeWithTag(PetPhotoPickerTestTags.REMOVE_BUTTON).assertIsDisplayed()
    composeRule.onNodeWithTag(PetPhotoPickerTestTags.ADD_BUTTON).assertDoesNotExist()
  }

  @Test
  fun withPhoto_takePhotoButtonRequestsTheCamera() {
    setContent(photoUri = "content://test/photo.jpg")

    composeRule.onNodeWithTag(PetPhotoPickerTestTags.TAKE_PHOTO_BUTTON).performClick()

    assertEquals(1, takeCalls)
    assertEquals(0, pickCalls)
    assertEquals(0, removeCalls)
  }

  @Test
  fun changeButton_requestsThePickerAgain() {
    setContent(photoUri = "content://test/photo.jpg")

    composeRule.onNodeWithTag(PetPhotoPickerTestTags.CHANGE_BUTTON).performClick()

    assertEquals(1, pickCalls)
    assertEquals(0, removeCalls)
  }

  @Test
  fun removeButton_removesThePhoto() {
    setContent(photoUri = "content://test/photo.jpg")

    composeRule.onNodeWithTag(PetPhotoPickerTestTags.REMOVE_BUTTON).performClick()

    assertEquals(1, removeCalls)
    assertEquals(0, pickCalls)
  }

  // ---------- PetPhotoPicker wired to the system picker and the camera app ----------

  /**
   * Replaces the real system activities (Photo Picker, camera app): when one is launched, it
   * records the contract and input, then immediately answers with [result].
   * - Photo Picker: a Uri = photo chosen, null = cancelled.
   * - Camera: true = photo saved, false = cancelled.
   */
  private class FakeActivityRegistry(private val result: Any?) : ActivityResultRegistry() {
    val launchedContracts = mutableListOf<ActivityResultContract<*, *>>()
    val launchedInputs = mutableListOf<Any?>()

    override fun <I, O> onLaunch(
      requestCode: Int,
      contract: ActivityResultContract<I, O>,
      input: I,
      options: ActivityOptionsCompat?,
    ) {
      launchedContracts += contract
      launchedInputs += input
      @Suppress("UNCHECKED_CAST") dispatchResult(requestCode, result as O)
    }
  }

  /** Records every value PetPhotoPicker sends to onPhotoSelected (including null). */
  private val selections = mutableListOf<String?>()

  private fun setPicker(photoUri: String?, registry: FakeActivityRegistry) {
    val owner =
      object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry = registry
      }
    composeRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
        PetPhotoPicker(
          photoUri = photoUri,
          onPhotoSelected = { selections += it },
          // CI emulators may have no camera: force the button so the wiring is always tested.
          canTakePhoto = true,
        )
      }
    }
  }

  // Gallery

  @Test
  fun choosingAPhoto_sendsItsUriToOnPhotoSelected() {
    val registry = FakeActivityRegistry(result = Uri.parse("content://media/picked/1"))
    setPicker(photoUri = null, registry = registry)

    composeRule.onNodeWithTag(PetPhotoPickerTestTags.ADD_BUTTON).performClick()
    composeRule.waitForIdle()

    assertEquals(listOf<String?>("content://media/picked/1"), selections)
  }

  @Test
  fun picker_isLaunchedForImagesOnly() {
    val registry = FakeActivityRegistry(result = null)
    setPicker(photoUri = null, registry = registry)

    composeRule.onNodeWithTag(PetPhotoPickerTestTags.ADD_BUTTON).performClick()
    composeRule.waitForIdle()

    val request = registry.launchedInputs.single() as PickVisualMediaRequest
    assertEquals(PickVisualMedia.ImageOnly, request.mediaType)
  }

  @Test
  fun cancellingThePicker_keepsTheCurrentPhoto() {
    val registry = FakeActivityRegistry(result = null)
    setPicker(photoUri = "content://test/current.jpg", registry = registry)

    composeRule.onNodeWithTag(PetPhotoPickerTestTags.CHANGE_BUTTON).performClick()
    composeRule.waitForIdle()

    assertEquals(1, registry.launchedInputs.size) // the picker did open
    assertTrue(selections.isEmpty()) // but nothing was sent: the photo stays the same
  }

  @Test
  fun removeButton_sendsNullToOnPhotoSelected() {
    val registry = FakeActivityRegistry(result = null)
    setPicker(photoUri = "content://test/current.jpg", registry = registry)

    composeRule.onNodeWithTag(PetPhotoPickerTestTags.REMOVE_BUTTON).performClick()
    composeRule.waitForIdle()

    assertEquals(listOf<String?>(null), selections)
    assertTrue(registry.launchedInputs.isEmpty()) // removing never opens anything
  }

  // Camera

  @Test
  fun takePhoto_launchesCameraWithAFileProviderUri() {
    val registry = FakeActivityRegistry(result = false)
    setPicker(photoUri = null, registry = registry)

    composeRule.onNodeWithTag(PetPhotoPickerTestTags.TAKE_PHOTO_BUTTON).performClick()
    composeRule.waitForIdle()

    assertTrue(registry.launchedContracts.single() is TakePicture)
    val target = registry.launchedInputs.single() as Uri
    assertEquals("content", target.scheme)
    assertEquals(CameraPhotoFile.authority(context), target.authority)
  }

  @Test
  fun takingAPhoto_sendsTheFileUriToOnPhotoSelected() {
    val registry = FakeActivityRegistry(result = true)
    setPicker(photoUri = null, registry = registry)

    composeRule.onNodeWithTag(PetPhotoPickerTestTags.TAKE_PHOTO_BUTTON).performClick()
    composeRule.waitForIdle()

    val target = registry.launchedInputs.single() as Uri
    assertEquals(listOf<String?>(target.toString()), selections)
  }

  @Test
  fun cancellingTheCamera_keepsTheCurrentPhoto() {
    val registry = FakeActivityRegistry(result = false)
    setPicker(photoUri = "content://test/current.jpg", registry = registry)

    composeRule.onNodeWithTag(PetPhotoPickerTestTags.TAKE_PHOTO_BUTTON).performClick()
    composeRule.waitForIdle()

    assertEquals(1, registry.launchedInputs.size) // the camera did open
    assertTrue(selections.isEmpty()) // but nothing was sent: the photo stays the same
  }

  // ---------- CameraPhotoFile ----------

  @Test
  fun newPhotoUri_createsAWritableFileSharedByOurProvider() {
    val uri = CameraPhotoFile.newPhotoUri(context)

    assertEquals(CameraPhotoFile.authority(context), uri.authority)
    // The camera app writes through this URI; check we can too.
    requireNotNull(context.contentResolver.openOutputStream(uri)).use {
      it.write(byteArrayOf(1, 2, 3))
    }
    requireNotNull(context.contentResolver.openInputStream(uri)).use {
      assertEquals(3, it.readBytes().size)
    }

    context.contentResolver.delete(uri, null, null)
  }

  @Test
  fun newPhotoUri_givesEachPhotoItsOwnFile() {
    val first = CameraPhotoFile.newPhotoUri(context)
    val second = CameraPhotoFile.newPhotoUri(context)

    assertTrue(first != second)

    context.contentResolver.delete(first, null, null)
    context.contentResolver.delete(second, null, null)
  }
}