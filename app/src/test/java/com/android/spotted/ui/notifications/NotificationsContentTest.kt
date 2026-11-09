// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.notifications

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.model.fakes.FakeNotificationRepository
import com.android.spotted.model.notification.SightingNotification
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI tests for [NotificationsContent] with hand-built states and a fixed current time. */
@RunWith(AndroidJUnit4::class)
class NotificationsContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun setContent(
      state: NotificationsUiState,
      onOpen: (SightingNotification) -> Unit = {},
      onBack: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      NotificationsContent(state = state, nowMillis = NOW, onOpen = onOpen, onBack = onBack)
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  @Test
  fun loading_showsOnlyTheSpinner() {
    setContent(NotificationsUiState(isLoading = true))

    node(NotificationsScreenTestTags.LOADING).assertIsDisplayed()
    node(NotificationsScreenTestTags.LIST).assertDoesNotExist()
    node(NotificationsScreenTestTags.EMPTY).assertDoesNotExist()
  }

  @Test
  fun list_showsEachSightingWithThePetNameAndHowLongAgo() {
    setContent(NotificationsUiState(isLoading = false, items = listOf(MILO, REX)))

    node(NotificationsScreenTestTags.LIST).assertIsDisplayed()
    node(NotificationsScreenTestTags.item(MILO.sightingId)).assertIsDisplayed()
    node(NotificationsScreenTestTags.item(REX.sightingId)).assertIsDisplayed()
    composeTestRule.onNodeWithText("Someone saw Milo").assertIsDisplayed()
    composeTestRule.onNodeWithText("12 min ago").assertIsDisplayed()
    composeTestRule.onNodeWithText("2 h ago").assertIsDisplayed()
    node(NotificationsScreenTestTags.EMPTY).assertDoesNotExist()
  }

  @Test
  fun empty_showsNoNewSightings() {
    setContent(NotificationsUiState(isLoading = false))

    node(NotificationsScreenTestTags.EMPTY).assertTextEquals("No new sightings")
    node(NotificationsScreenTestTags.LIST).assertDoesNotExist()
  }

  @Test
  fun errorWithoutAList_showsOnlyTheError() {
    setContent(NotificationsUiState(isLoading = false, errorMessage = "Couldn't load."))

    node(NotificationsScreenTestTags.ERROR).assertTextEquals("Couldn't load.")
    node(NotificationsScreenTestTags.EMPTY).assertDoesNotExist()
    node(NotificationsScreenTestTags.LIST).assertDoesNotExist()
  }

  @Test
  fun errorWithAList_keepsTheListBelowTheError() {
    setContent(
        NotificationsUiState(
            isLoading = false,
            items = listOf(MILO),
            errorMessage = "Couldn't load.",
        )
    )

    node(NotificationsScreenTestTags.ERROR).assertIsDisplayed()
    node(NotificationsScreenTestTags.item(MILO.sightingId)).assertIsDisplayed()
  }

  @Test
  fun tappingASighting_opensIt() {
    val opened = mutableListOf<SightingNotification>()
    setContent(
        NotificationsUiState(isLoading = false, items = listOf(MILO, REX)),
        onOpen = { opened += it },
    )

    node(NotificationsScreenTestTags.item(REX.sightingId)).performClick()

    assertEquals(listOf(REX), opened)
  }

  @Test
  fun back_reportsTheTap() {
    var back = false
    setContent(NotificationsUiState(isLoading = false), onBack = { back = true })

    node(NotificationsScreenTestTags.BACK_BUTTON).performClick()

    assertTrue(back)
  }

  @Test
  fun screen_opensTheAlertOnce_andClearsTheDestination() {
    val auth = FakeAuthRepository()
    runBlocking { auth.signInWithGoogle("token") }
    val notifications = FakeNotificationRepository()
    notifications.setPending(auth.currentUser.value!!.uid, listOf(MILO))
    val viewModel = NotificationsViewModel(auth, notifications)
    val opened = mutableListOf<String>()
    composeTestRule.setContent {
      NotificationsScreen(viewModel = viewModel, onOpenAlert = { opened += it }, now = { NOW })
    }

    node(NotificationsScreenTestTags.item(MILO.sightingId)).performClick()
    composeTestRule.waitForIdle()

    assertEquals(listOf(MILO.alertId), opened)
    assertNull(viewModel.uiState.value.destinationAlertId)
    composeTestRule.onNodeWithText("12 min ago").assertIsDisplayed()
  }

  private companion object {
    const val NOW = 1_700_000_000_000L
    const val MINUTE = 60_000L

    val MILO =
        SightingNotification(
            sightingId = "s-milo",
            alertId = "alert-milo",
            petName = "Milo",
            sightingPhotoUrl = "https://example.com/milo.jpg",
            seenAtMillis = NOW - 12 * MINUTE,
        )
    val REX =
        SightingNotification(
            sightingId = "s-rex",
            alertId = "alert-rex",
            petName = "Rex",
            sightingPhotoUrl = null,
            seenAtMillis = NOW - 120 * MINUTE,
        )
  }
}
