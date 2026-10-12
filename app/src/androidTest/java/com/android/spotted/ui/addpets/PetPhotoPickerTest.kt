package com.android.spotted.ui.addpets

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PetPhotoPickerTest {

  @get:Rule val composeRule = createComposeRule()

  // ---------- Stateless content: rendering and callbacks ----------

  private var pickCalls = 0
  private var removeCalls = 0

  private fun setContent(photoUri: String?) {
    composeRule.setContent {
      PetPhotoPickerContent(
          photoUri = photoUri,
          onPickPhoto = { pickCalls++ },
          onRemovePhoto = { removeCalls++ },
      )
    }
  }

  @Test
  fun withoutPhoto_showsAddButtonOnly() {
    setContent(photoUri = null)

    composeRule.onNodeWithTag(PetPhotoPickerTestTags.ADD_BUTTON).assertIsDisplayed()
    composeRule.onNodeWithTag(PetPhotoPickerTestTags.PREVIEW).assertDoesNotExist()
    composeRule.onNodeWithTag(PetPhotoPickerTestTags.REMOVE_BUTTON).assertDoesNotExist()
  }

  @Test
  fun addButton_requestsThePicker() {
    setContent(photoUri = null)

    composeRule.onNodeWithTag(PetPhotoPickerTestTags.ADD_BUTTON).performClick()

    assertEquals(1, pickCalls)
  }

  @Test
  fun withPhoto_showsPreviewAndActions() {
    setContent(photoUri = "content://test/photo.jpg")

    composeRule.onNodeWithTag(PetPhotoPickerTestTags.PREVIEW).assertIsDisplayed()
    composeRule.onNodeWithTag(PetPhotoPickerTestTags.CHANGE_BUTTON).assertIsDisplayed()
    composeRule.onNodeWithTag(PetPhotoPickerTestTags.REMOVE_BUTTON).assertIsDisplayed()
    composeRule.onNodeWithTag(PetPhotoPickerTestTags.ADD_BUTTON).assertDoesNotExist()
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

  // ---------- PetPhotoPicker wired to the system picker ----------

  /**
   * Replaces the real system Photo Picker: when the picker is launched, it records the request and
   * immediately answers with [result] (a URI = photo chosen, null = picker cancelled).
   */
  private class FakePickerRegistry(private val result: Uri?) : ActivityResultRegistry() {
    val launchedInputs = mutableListOf<Any?>()

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
      launchedInputs += input
      @Suppress("UNCHECKED_CAST") dispatchResult(requestCode, result as O)
    }
  }

  /** Records every value PetPhotoPicker sends to onPhotoSelected (including null). */
  private val selections = mutableListOf<String?>()

  private fun setPicker(photoUri: String?, registry: FakePickerRegistry) {
    val owner =
        object : ActivityResultRegistryOwner {
          override val activityResultRegistry: ActivityResultRegistry = registry
        }
    composeRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
        PetPhotoPicker(photoUri = photoUri, onPhotoSelected = { selections += it })
      }
    }
  }

  @Test
  fun choosingAPhoto_sendsItsUriToOnPhotoSelected() {
    val registry = FakePickerRegistry(result = Uri.parse("content://media/picked/1"))
    setPicker(photoUri = null, registry = registry)

    composeRule.onNodeWithTag(PetPhotoPickerTestTags.ADD_BUTTON).performClick()
    composeRule.waitForIdle()

    assertEquals(listOf<String?>("content://media/picked/1"), selections)
  }

  @Test
  fun picker_isLaunchedForImagesOnly() {
    val registry = FakePickerRegistry(result = null)
    setPicker(photoUri = null, registry = registry)

    composeRule.onNodeWithTag(PetPhotoPickerTestTags.ADD_BUTTON).performClick()
    composeRule.waitForIdle()

    val request = registry.launchedInputs.single() as PickVisualMediaRequest
    assertEquals(PickVisualMedia.ImageOnly, request.mediaType)
  }

  @Test
  fun cancellingThePicker_keepsTheCurrentPhoto() {
    val registry = FakePickerRegistry(result = null)
    setPicker(photoUri = "content://test/current.jpg", registry = registry)

    composeRule.onNodeWithTag(PetPhotoPickerTestTags.CHANGE_BUTTON).performClick()
    composeRule.waitForIdle()

    assertEquals(1, registry.launchedInputs.size) // the picker did open
    assertTrue(selections.isEmpty()) // but nothing was sent: the photo stays the same
  }

  @Test
  fun removeButton_sendsNullToOnPhotoSelected() {
    val registry = FakePickerRegistry(result = null)
    setPicker(photoUri = "content://test/current.jpg", registry = registry)

    composeRule.onNodeWithTag(PetPhotoPickerTestTags.REMOVE_BUTTON).performClick()
    composeRule.waitForIdle()

    assertEquals(listOf<String?>(null), selections)
    assertTrue(registry.launchedInputs.isEmpty()) // removing never opens the picker
  }
}
