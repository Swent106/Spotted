package com.android.spotted.ui.personalinfo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.model.auth.User
import com.android.spotted.model.user.FakeUserRepository
import org.junit.Assert.assertEquals
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
      onUsernameChange: (String) -> Unit = {},
      onFirstNameChange: (String) -> Unit = {},
      onLastNameChange: (String) -> Unit = {},
      onHomeAreaChange: (String) -> Unit = {},
      onCreateProfileClick: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      PersonalInfoContent(
          state = state,
          onBackClick = onBackClick,
          onChangePhotoClick = {},
          onUsernameChange = onUsernameChange,
          onFirstNameChange = onFirstNameChange,
          onLastNameChange = onLastNameChange,
          onHomeAreaChange = onHomeAreaChange,
          onUseMyLocationClick = {},
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
  fun typingOtherFields_callsTheirCallbacks() {
    var username = ""
    var lastName = ""
    var homeArea = ""
    setContent(
        PersonalInfoUiState(),
        onUsernameChange = { username = it },
        onLastNameChange = { lastName = it },
        onHomeAreaChange = { homeArea = it },
    )

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

    assertEquals("@rayen.gva", username)
    assertEquals("Ben Ali", lastName)
    assertEquals("Plainpalais, Geneva", homeArea)
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
    button().performScrollTo().performClick()
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
}
