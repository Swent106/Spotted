package com.android.spotted.ui.personalinfo

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.app.ActivityOptionsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.model.auth.User
import com.android.spotted.model.user.FakeUserRepository
import com.android.spotted.ui.addpets.CameraPhotoFile
import com.android.spotted.ui.components.DefaultMapCenter
import com.android.spotted.ui.components.MapPickerTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for the stateless [PersonalInfoContent], plus one test of [PersonalInfoScreen] wired to
 * a [PersonalInfoViewModel] backed by fakes.
 */
class PersonalInfoScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val validState =
      PersonalInfoUiState(
          firstName = "Rayen",
          lastName = "Ben Ali",
          name = "Rayen Ben Ali",
          homeArea = "Plainpalais, Geneva",
      )

  private fun setContent(
      state: PersonalInfoUiState,
      onBackClick: () -> Unit = {},
      onFirstNameChange: (String) -> Unit = {},
      onAlertRadiusSelect: (Int) -> Unit = {},
      onCreateProfileClick: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      PersonalInfoContent(
          state = state,
          onBackClick = onBackClick,
          onChangePhotoClick = {},
          onUsernameChange = {},
          onFirstNameChange = onFirstNameChange,
          onLastNameChange = {},
          onHomeAreaChange = {},
          onUseMyLocationClick = {},
          onHomeLocationChange = {},
          onAlertRadiusSelect = onAlertRadiusSelect,
          onCreateProfileClick = onCreateProfileClick,
      )
    }
  }

  private fun button() =
      composeTestRule.onNodeWithTag(PersonalInfoScreenTestTags.CREATE_PROFILE_BUTTON)

  @Test
  fun emptyForm_showsAllFields_andDisablesCreateProfile() {
    setContent(PersonalInfoUiState())

    listOf(
            PersonalInfoScreenTestTags.BACK_BUTTON,
            PersonalInfoScreenTestTags.AVATAR,
            PersonalInfoScreenTestTags.USERNAME_INPUT,
            PersonalInfoScreenTestTags.FIRST_NAME_INPUT,
            PersonalInfoScreenTestTags.LAST_NAME_INPUT,
            PersonalInfoScreenTestTags.HOME_AREA_INPUT,
            MapPickerTestTags.MAP,
        )
        .forEach { composeTestRule.onNodeWithTag(it).performScrollTo().assertIsDisplayed() }
    button().assertIsDisplayed().assertIsNotEnabled()
  }

  @Test
  fun missingHomeArea_disablesCreateProfile() {
    setContent(validState.copy(homeArea = ""))

    button().assertIsNotEnabled()
  }

  @Test
  fun missingName_disablesCreateProfile() {
    setContent(validState.copy(firstName = "", lastName = "", name = ""))

    button().assertIsNotEnabled()
  }

  @Test
  fun validForm_enablesCreateProfile_andClickCallsCallback() {
    var clicks = 0
    setContent(validState, onCreateProfileClick = { clicks++ })

    button().assertIsEnabled().performClick()

    assertEquals(1, clicks)
  }

  @Test
  fun savingState_showsLoader_andDisablesCreateProfile() {
    setContent(validState.copy(isSaving = true))

    composeTestRule.onNodeWithTag(PersonalInfoScreenTestTags.LOADER).assertIsDisplayed()
    button().assertIsNotEnabled()
  }

  @Test
  fun typingFirstName_callsOnFirstNameChange() {
    var typed = ""
    setContent(PersonalInfoUiState(), onFirstNameChange = { typed = it })

    composeTestRule
        .onNodeWithTag(PersonalInfoScreenTestTags.FIRST_NAME_INPUT)
        .performScrollTo()
        .performTextInput("Rayen")

    assertEquals("Rayen", typed)
  }

  @Test
  fun radiusChips_showSelection_andReportClicks() {
    var selected = 0
    setContent(
        PersonalInfoUiState(homeLocation = DefaultMapCenter),
        onAlertRadiusSelect = { selected = it },
    )

    composeTestRule
        .onNodeWithTag(PersonalInfoScreenTestTags.radiusChip(3))
        .performScrollTo()
        .assertIsSelected()
    listOf(1, 5, 10).forEach {
      composeTestRule.onNodeWithTag(PersonalInfoScreenTestTags.radiusChip(it)).assertIsNotSelected()
    }
    composeTestRule
        .onNodeWithTag(MapPickerTestTags.RADIUS_LABEL, useUnmergedTree = true)
        .performScrollTo()
        .assertTextEquals("Alerts within 3 km")

    PersonalInfoUiState.ALERT_RADIUS_OPTIONS_KM.forEach { radiusKm ->
      composeTestRule.onNodeWithTag(PersonalInfoScreenTestTags.radiusChip(radiusKm)).performClick()
      assertEquals(radiusKm, selected)
    }
  }

  @Test
  fun typingOtherFields_callsTheirCallbacks() {
    // The callbacks update the state, as the ViewModel does: a field whose value never changes
    // reports its old value again when it loses focus.
    var state by mutableStateOf(PersonalInfoUiState())
    composeTestRule.setContent {
      PersonalInfoContent(
          state = state,
          onBackClick = {},
          onChangePhotoClick = {},
          onUsernameChange = { state = state.copy(username = it) },
          onFirstNameChange = {},
          onLastNameChange = { state = state.copy(lastName = it) },
          onHomeAreaChange = { state = state.copy(homeArea = it) },
          onUseMyLocationClick = {},
          onHomeLocationChange = {},
          onAlertRadiusSelect = {},
          onCreateProfileClick = {},
      )
    }

    composeTestRule
        .onNodeWithTag(PersonalInfoScreenTestTags.USERNAME_INPUT)
        .performScrollTo()
        .performTextInput("@rayen.gva")
    composeTestRule
        .onNodeWithTag(PersonalInfoScreenTestTags.LAST_NAME_INPUT)
        .performScrollTo()
        .performTextInput("Ben Ali")
    composeTestRule
        .onNodeWithTag(PersonalInfoScreenTestTags.HOME_AREA_INPUT)
        .performScrollTo()
        .performTextInput("Plainpalais, Geneva")

    assertEquals("@rayen.gva", state.username)
    assertEquals("Ben Ali", state.lastName)
    assertEquals("Plainpalais, Geneva", state.homeArea)
  }

  @Test
  fun backButton_callsOnBackClick() {
    var clicks = 0
    setContent(PersonalInfoUiState(), onBackClick = { clicks++ })

    composeTestRule.onNodeWithTag(PersonalInfoScreenTestTags.BACK_BUTTON).performClick()

    assertEquals(1, clicks)
  }

  @Test
  fun errorMessage_isShown() {
    setContent(validState.copy(errorMessage = "Unable to save profile"))

    composeTestRule
        .onNodeWithTag(PersonalInfoScreenTestTags.ERROR_MESSAGE)
        .performScrollTo()
        .assertTextEquals("Unable to save profile")
  }

  @Test
  fun noErrorMessage_isShownWhenErrorIsNull() {
    setContent(validState)

    composeTestRule.onNodeWithTag(PersonalInfoScreenTestTags.ERROR_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun personalInfoScreen_callsOnSavedOnce_evenWhenShownAgain() {
    val viewModel =
        PersonalInfoViewModel(
            user = User(uid = "user-1", email = "rayen@example.com"),
            userRepository = FakeUserRepository(),
            authRepository = FakeAuthRepository(),
        )
    var savedCalls = 0
    var isShown by mutableStateOf(true)
    composeTestRule.setContent {
      if (isShown) PersonalInfoScreen(viewModel = viewModel, onSaved = { savedCalls++ })
    }

    viewModel.onFirstNameChanged("Rayen")
    viewModel.onHomeAreaChanged("Plainpalais, Geneva")
    button().performClick()
    composeTestRule.waitUntil { savedCalls == 1 }

    // Simulates pressing Back from the next screen: Personal Info is composed again.
    isShown = false
    composeTestRule.waitForIdle()
    isShown = true
    composeTestRule.waitForIdle()

    assertEquals(1, savedCalls)
  }

  @Test
  fun personalInfoScreen_withViewModel_enablesButtonOnceFormIsFilled() {
    val viewModel =
        PersonalInfoViewModel(
            user = User(uid = "user-1", email = "rayen@example.com"),
            userRepository = FakeUserRepository(),
            authRepository = FakeAuthRepository(),
        )
    composeTestRule.setContent { PersonalInfoScreen(viewModel = viewModel) }

    button().assertIsNotEnabled()
    composeTestRule
        .onNodeWithTag(PersonalInfoScreenTestTags.FIRST_NAME_INPUT)
        .performScrollTo()
        .performTextInput("Rayen")
    composeTestRule
        .onNodeWithTag(PersonalInfoScreenTestTags.HOME_AREA_INPUT)
        .performScrollTo()
        .performTextInput("Plainpalais, Geneva")

    button().assertIsEnabled()
  }

  @Test
  fun photoSelected_isShownInTheAvatar() {
    setContent(validState.copy(photoUri = "content://photos/1"))

    // The photo is drawn inside the clickable avatar, which merges its children
    composeTestRule
        .onNodeWithTag(PersonalInfoScreenTestTags.PHOTO, useUnmergedTree = true)
        .assertExists()
  }

  @Test
  fun personalInfoScreen_avatarOpensPhotoOptions_andCancelClosesThem() {
    val viewModel =
        PersonalInfoViewModel(
            user = User(uid = "user-1", email = "rayen@example.com"),
            userRepository = FakeUserRepository(),
            authRepository = FakeAuthRepository(),
        )
    composeTestRule.setContent { PersonalInfoScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithTag(PersonalInfoScreenTestTags.CHANGE_PHOTO).performClick()

    composeTestRule.onNodeWithTag(PersonalInfoScreenTestTags.PHOTO_FROM_GALLERY).assertIsDisplayed()
    // Nothing to remove yet
    composeTestRule.onNodeWithTag(PersonalInfoScreenTestTags.PHOTO_REMOVE).assertDoesNotExist()

    composeTestRule.onNodeWithText("Cancel").performClick()

    composeTestRule.onNodeWithTag(PersonalInfoScreenTestTags.PHOTO_OPTIONS).assertDoesNotExist()
  }

  @Test
  fun personalInfoScreen_removePhoto_clearsTheSelectedPhoto() {
    val viewModel =
        PersonalInfoViewModel(
            user = User(uid = "user-1", email = "rayen@example.com"),
            userRepository = FakeUserRepository(),
            authRepository = FakeAuthRepository(),
        )
    viewModel.onPhotoSelected("content://photos/1")
    composeTestRule.setContent { PersonalInfoScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithTag(PersonalInfoScreenTestTags.CHANGE_PHOTO).performClick()
    composeTestRule.onNodeWithTag(PersonalInfoScreenTestTags.PHOTO_REMOVE).performClick()
    composeTestRule.waitForIdle()

    assertNull(viewModel.uiState.value.photoUri)
    composeTestRule.onNodeWithTag(PersonalInfoScreenTestTags.PHOTO_OPTIONS).assertDoesNotExist()
  }

  @Test
  fun personalInfoScreen_galleryPick_selectsThePickedPhoto() {
    val viewModel = photoViewModel()
    setScreenWithResult(viewModel, Uri.parse("content://photos/picked"))

    composeTestRule.onNodeWithTag(PersonalInfoScreenTestTags.CHANGE_PHOTO).performClick()
    composeTestRule.onNodeWithTag(PersonalInfoScreenTestTags.PHOTO_FROM_GALLERY).performClick()
    composeTestRule.waitForIdle()

    assertEquals("content://photos/picked", viewModel.uiState.value.photoUri)
    composeTestRule.onNodeWithTag(PersonalInfoScreenTestTags.PHOTO_OPTIONS).assertDoesNotExist()
  }

  @Test
  fun personalInfoScreen_closingTheGallery_keepsTheCurrentPhoto() {
    val viewModel = photoViewModel().apply { onPhotoSelected("content://photos/1") }
    setScreenWithResult(viewModel, result = null)

    composeTestRule.onNodeWithTag(PersonalInfoScreenTestTags.CHANGE_PHOTO).performClick()
    composeTestRule.onNodeWithTag(PersonalInfoScreenTestTags.PHOTO_FROM_GALLERY).performClick()
    composeTestRule.waitForIdle()

    assertEquals("content://photos/1", viewModel.uiState.value.photoUri)
  }

  @Test
  fun personalInfoScreen_cameraPhoto_isSelectedOnceSaved() {
    assumeTrue(CameraPhotoFile.deviceHasCamera(context()))
    val viewModel = photoViewModel()
    setScreenWithResult(viewModel, result = true)

    composeTestRule.onNodeWithTag(PersonalInfoScreenTestTags.CHANGE_PHOTO).performClick()
    composeTestRule.onNodeWithTag(PersonalInfoScreenTestTags.PHOTO_FROM_CAMERA).performClick()
    composeTestRule.waitForIdle()

    val photoUri = viewModel.uiState.value.photoUri
    assertTrue(photoUri.toString(), photoUri?.startsWith("content://") == true)
  }

  @Test
  fun personalInfoScreen_cancelledCameraPhoto_isNotSelected() {
    assumeTrue(CameraPhotoFile.deviceHasCamera(context()))
    val viewModel = photoViewModel()
    setScreenWithResult(viewModel, result = false)

    composeTestRule.onNodeWithTag(PersonalInfoScreenTestTags.CHANGE_PHOTO).performClick()
    composeTestRule.onNodeWithTag(PersonalInfoScreenTestTags.PHOTO_FROM_CAMERA).performClick()
    composeTestRule.waitForIdle()

    assertNull(viewModel.uiState.value.photoUri)
  }

  private fun context() = InstrumentationRegistry.getInstrumentation().targetContext

  private fun photoViewModel() =
      PersonalInfoViewModel(
          user = User(uid = "user-1", email = "rayen@example.com"),
          userRepository = FakeUserRepository(),
          authRepository = FakeAuthRepository(),
      )

  /**
   * Shows [PersonalInfoScreen] with a registry that answers every launch with [result] at once, so
   * the gallery and the camera are never opened.
   */
  private fun setScreenWithResult(viewModel: PersonalInfoViewModel, result: Any?) {
    val registry =
        object : ActivityResultRegistry() {
          override fun <I, O> onLaunch(
              requestCode: Int,
              contract: ActivityResultContract<I, O>,
              input: I,
              options: ActivityOptionsCompat?,
          ) {
            @Suppress("UNCHECKED_CAST") dispatchResult(requestCode, result as O)
          }
        }
    val owner =
        object : ActivityResultRegistryOwner {
          override val activityResultRegistry: ActivityResultRegistry = registry
        }
    composeTestRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
        PersonalInfoScreen(viewModel = viewModel)
      }
    }
  }

  @Test
  fun photoOptions_removeIsOfferedOnlyWithAPhoto_andCallsBack() {
    var removed = false
    composeTestRule.setContent {
      PhotoOptionsDialog(
          hasPhoto = true,
          canTakePhoto = true,
          onPickPhoto = {},
          onTakePhoto = {},
          onRemovePhoto = { removed = true },
          onDismiss = {},
      )
    }

    composeTestRule.onNodeWithTag(PersonalInfoScreenTestTags.PHOTO_FROM_CAMERA).assertIsDisplayed()
    composeTestRule.onNodeWithTag(PersonalInfoScreenTestTags.PHOTO_REMOVE).performClick()

    assertTrue(removed)
  }
}
