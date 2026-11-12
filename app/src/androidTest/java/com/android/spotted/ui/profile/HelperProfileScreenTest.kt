// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Pet.PetRepositoryLocal
import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.model.user.FakeUserRepository
import com.android.spotted.model.user.UserProfile
import com.android.spotted.utils.FakeOpenAlertsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** [HelperProfileScreen] wires the account dialogs to its ViewModel. */
@RunWith(AndroidJUnit4::class)
class HelperProfileScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val authRepository = FakeAuthRepository()
  private val userRepository = FakeUserRepository()
  private lateinit var uid: String
  private lateinit var viewModel: HelperProfileViewModel

  @Before
  fun setUp() = runTest {
    uid = authRepository.signInWithGoogle("token").getOrThrow().uid
    userRepository.saveUser(UserProfile(uid = uid, name = "Rayen", email = "rayen@example.com"))
    viewModel =
        HelperProfileViewModel(
            userRepository,
            authRepository,
            PetRepositoryLocal(),
            FakeOpenAlertsRepository(),
        )
    composeTestRule.setContent { HelperProfileScreen(viewModel = viewModel) }
    composeTestRule.waitForIdle()
  }

  private fun askToDelete() {
    composeTestRule.onNodeWithTag(HelperProfileTestTags.ACCOUNT_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(HelperProfileTestTags.DELETE_ACCOUNT).performClick()
    composeTestRule.waitForIdle()
  }

  private fun isShown(tag: String): Boolean =
      composeTestRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

  @Test
  fun deleteAccount_opensTheConfirmationInsteadOfDeleting() {
    askToDelete()

    composeTestRule.onNodeWithTag(HelperProfileTestTags.DELETE_CONFIRM_DIALOG).assertIsDisplayed()
    assertFalse(isShown(HelperProfileTestTags.ACCOUNT_OPTIONS))
    assertEquals(uid, authRepository.currentUser.value?.uid)
  }

  @Test
  fun cancelOnTheConfirmation_closesItAndDeletesNothing() {
    askToDelete()

    composeTestRule.onNodeWithTag(HelperProfileTestTags.DELETE_CANCEL).performClick()
    composeTestRule.waitForIdle()

    assertFalse(isShown(HelperProfileTestTags.DELETE_CONFIRM_DIALOG))
    assertFalse(viewModel.uiState.value.isDeleteConfirmationVisible)
    assertEquals(uid, authRepository.currentUser.value?.uid)
  }

  @Test
  fun deleteOnTheConfirmation_closesItAndDeletesTheAccount() = runTest {
    askToDelete()

    composeTestRule.onNodeWithTag(HelperProfileTestTags.DELETE_CONFIRM).performClick()

    composeTestRule.waitForIdle()
    assertFalse(isShown(HelperProfileTestTags.DELETE_CONFIRM_DIALOG))
    // Credential Manager is cleared on the device first, then the account is deleted
    composeTestRule.waitUntil(timeoutMillis = 5_000) { authRepository.currentUser.value == null }
    assertTrue(userRepository.getUser(uid).isFailure)
  }
}
