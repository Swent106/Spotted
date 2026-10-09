package com.android.spotted.ui.authentification

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.android.spotted.model.auth.FakeAuthRepository
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for the stateless [SignInContent].
 *
 * Each test renders the screen with a hand-built [SignInUiState] (no ViewModel, no Firebase) and
 * checks what is shown. Elements in the lower part of the screen are scrolled into view first, so
 * the tests also pass on small screens (e.g. the CI emulator).
 */
class SignInContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun setContent(state: SignInUiState, onSignInClick: () -> Unit = {}) {
    composeTestRule.setContent { SignInContent(state = state, onSignInClick = onSignInClick) }
  }

  @Test
  fun idleState_showsGoogleButton_withoutLoaderOrError() {
    setContent(SignInUiState())

    composeTestRule.onNodeWithTag(SignInScreenTestTags.SCREEN).assertIsDisplayed()
    composeTestRule
      .onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON)
      .performScrollTo()
      .assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOADER).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.ERROR_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun loadingState_showsLoader_andHidesGoogleButton() {
    setContent(SignInUiState(isLoading = true))

    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOADER).performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON).assertDoesNotExist()
  }

  @Test
  fun errorState_showsErrorMessage() {
    setContent(SignInUiState(errorMessage = "Sign-in cancelled"))

    composeTestRule
      .onNodeWithTag(SignInScreenTestTags.ERROR_MESSAGE)
      .performScrollTo()
      .assertIsDisplayed()
      .assertTextEquals("Sign-in cancelled")
  }

  @Test
  fun errorState_keepsGoogleButton_soTheUserCanRetry() {
    setContent(SignInUiState(errorMessage = "Network error"))

    composeTestRule
      .onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON)
      .performScrollTo()
      .assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOADER).assertDoesNotExist()
  }

  @Test
  fun clickingGoogleButton_callsOnSignInClick() {
    var clicks = 0
    setContent(SignInUiState(), onSignInClick = { clicks++ })

    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON).performScrollTo().performClick()

    assertEquals(1, clicks)
  }

  @Test
  fun signInScreen_withViewModel_showsGoogleButton() {
    val viewModel = SignInViewModel(FakeAuthRepository())
    composeTestRule.setContent { SignInScreen(viewModel = viewModel) }

    composeTestRule
      .onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON)
      .performScrollTo()
      .assertIsDisplayed()
  }
}