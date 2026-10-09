package com.android.spotted.ui.personalinfo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
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
class PersonalInfoContentTest {

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
            PersonalInfoScreenTestTags.MAP_PREVIEW,
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
    setContent(PersonalInfoUiState(), onAlertRadiusSelect = { selected = it })

    composeTestRule
        .onNodeWithTag(PersonalInfoScreenTestTags.radiusChip(3))
        .performScrollTo()
        .assertIsSelected()
    composeTestRule
        .onNodeWithTag(PersonalInfoScreenTestTags.RADIUS_TAG, useUnmergedTree = true)
        .performScrollTo()
        .assertTextEquals("Alerts within 3 km")

    composeTestRule.onNodeWithTag(PersonalInfoScreenTestTags.radiusChip(10)).performClick()

    assertEquals(10, selected)
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
