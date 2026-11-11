// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.authentification

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.model.auth.User
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for the stateless [SignInContent].
 *
 * Each test renders the screen with a hand-built [SignInUiState] (no ViewModel, no Firebase) and
 * checks what is shown. On a short screen the content scrolls, so the error message is scrolled to
 * before it is checked.
 */
class SignInContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun setContent(state: SignInUiState, onSignInClick: () -> Unit = {}) {
    composeTestRule.setContent { SignInContent(state = state, onSignInClick = onSignInClick) }
  }

  @Test
  fun screen_doesNotScroll() {
    setContent(SignInUiState())

    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.SCREEN)
        .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.ScrollBy))
  }

  @Test
  fun signedIn_keepsLoader_andNoGoogleButton() {
    setContent(SignInUiState(isLoading = true, user = User("uid", "a@b.ch")))

    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOADER).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON).assertDoesNotExist()
  }

  @Test
  fun idleState_showsGoogleButton_withoutLoaderOrError() {
    setContent(SignInUiState())

    composeTestRule.onNodeWithTag(SignInScreenTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOADER).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.ERROR_MESSAGE).assertDoesNotExist()
  }

  @Test
  fun loadingState_showsLoader_andHidesGoogleButton() {
    setContent(SignInUiState(isLoading = true))

    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOADER).assertIsDisplayed()
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

    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.LOADER).assertDoesNotExist()
  }

  @Test
  fun clickingGoogleButton_callsOnSignInClick() {
    var clicks = 0
    setContent(SignInUiState(), onSignInClick = { clicks++ })

    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON).performClick()

    assertEquals(1, clicks)
  }

  @Test
  fun landscape_withLargeFont_keepsButtonAndErrorReachable() {
    // A small phone in landscape with a large font. ForcedSize scales the density so the 640x360
    // screen fits the emulator, so "displayed" means on screen, not just composed.
    composeTestRule.setContent {
      DeviceConfigurationOverride(
          DeviceConfigurationOverride.ForcedSize(DpSize(640.dp, 360.dp)) then
              DeviceConfigurationOverride.FontScale(1.5f)
      ) {
        SignInContent(state = SignInUiState(errorMessage = "Sign-in cancelled"), onSignInClick = {})
      }
    }

    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON)
        .performScrollTo()
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.ERROR_MESSAGE)
        .performScrollTo()
        .assertIsDisplayed()
  }

  @Test
  fun signInScreen_withViewModel_showsGoogleButton() {
    val viewModel = SignInViewModel(FakeAuthRepository())
    composeTestRule.setContent { SignInScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON).assertIsDisplayed()
  }
}
