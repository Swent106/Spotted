// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.linktag

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** [LinkTagContent] with hand-built states: one test per step, then the buttons and the header. */
@RunWith(AndroidJUnit4::class)
class LinkTagContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val idle = LinkTagUiState(petName = "Luna")
  private val calls = mutableListOf<String>()

  private fun show(state: LinkTagUiState) {
    composeTestRule.setContent {
      LinkTagContent(
          state = state,
          onStart = { calls += "start" },
          onRetry = { calls += "retry" },
          onDone = { calls += "done" },
          onCancel = { calls += "cancel" },
      )
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  private fun click(tag: String) = node(tag).performClick()

  @Test
  fun idleShowsThePetsTagAndStartsOnClick() {
    show(idle)

    composeTestRule.onNodeWithText("Luna's tag").assertIsDisplayed()
    composeTestRule.onNodeWithText("Choose what finders see, then tap the tag").assertIsDisplayed()
    node(LinkTagScreenTestTags.STATUS_ICON).assertIsDisplayed()
    node(LinkTagScreenTestTags.STATUS_TITLE)
        .assertTextEquals("Hold the tag to the back of your phone")
    node(LinkTagScreenTestTags.STATUS_CHIP).assertDoesNotExist()
    node(LinkTagScreenTestTags.START_BUTTON).assertIsEnabled().performClick()

    assertEquals(listOf("start"), calls)
  }

  @Test
  fun startIsDisabledWhenNoContactIsShown() {
    show(idle.copy(showPhone = false, showEmail = false))

    node(LinkTagScreenTestTags.START_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun waitingShowsTheChipAndOffersOnlyCancel() {
    show(idle.copy(step = LinkTagStep.WaitingForTag))

    node(LinkTagScreenTestTags.STATUS_CHIP).assertTextEquals("Waiting for a tag…")
    node(LinkTagScreenTestTags.START_BUTTON).assertDoesNotExist()
    click(LinkTagScreenTestTags.CANCEL_BUTTON)

    assertEquals(listOf("cancel"), calls)
  }

  @Test
  fun writingShowsThePetAndTheWritingChip() {
    show(idle.copy(step = LinkTagStep.Writing))

    composeTestRule.onNodeWithText("Keep the tag against your phone").assertIsDisplayed()
    node(LinkTagScreenTestTags.STATUS_TITLE).assertTextEquals("Linking the tag to Luna…")
    node(LinkTagScreenTestTags.STATUS_CHIP).assertTextEquals("Writing to the tag")
    node(LinkTagScreenTestTags.START_BUTTON).assertDoesNotExist()
    node(LinkTagScreenTestTags.CANCEL_BUTTON).assertIsDisplayed()
  }

  @Test
  fun errorShowsTheReasonAndRetriesOnClick() {
    show(idle.copy(step = LinkTagStep.Error, errorMessage = R.string.link_tag_lost_error))

    composeTestRule.onNodeWithText("Nothing was linked").assertIsDisplayed()
    node(LinkTagScreenTestTags.STATUS_TITLE).assertTextEquals("Couldn't link this tag")
    node(LinkTagScreenTestTags.ERROR_MESSAGE)
        .assertTextEquals("The tag moved away too soon. Hold it against the phone and try again.")
    click(LinkTagScreenTestTags.RETRY_BUTTON)
    click(LinkTagScreenTestTags.CANCEL_BUTTON)

    assertEquals(listOf("retry", "cancel"), calls)
  }

  @Test
  fun errorWithoutAMessageShowsNoReason() {
    show(idle.copy(step = LinkTagStep.Error))

    node(LinkTagScreenTestTags.ERROR_MESSAGE).assertDoesNotExist()
    node(LinkTagScreenTestTags.RETRY_BUTTON).assertIsEnabled()
  }

  @Test
  fun petThatCouldNotBeLoadedOffersNoRetry() {
    show(LinkTagUiState(step = LinkTagStep.Error, errorMessage = R.string.link_tag_load_error))

    composeTestRule.onNodeWithText("Link a tag").assertIsDisplayed()
    node(LinkTagScreenTestTags.ERROR_MESSAGE).assertTextEquals("Unable to load the pet")
    node(LinkTagScreenTestTags.RETRY_BUTTON).assertDoesNotExist()
    node(LinkTagScreenTestTags.CANCEL_BUTTON).assertIsDisplayed()
  }

  @Test
  fun successShowsTheLinkedChipAndFinishesWithDone() {
    show(idle.copy(step = LinkTagStep.Success))

    composeTestRule
        .onNodeWithText("Anyone who taps it sees Luna, never your address")
        .assertIsDisplayed()
    node(LinkTagScreenTestTags.STATUS_TITLE).assertTextEquals("Tag linked to Luna")
    node(LinkTagScreenTestTags.STATUS_CHIP).assertTextEquals("✓ Tag linked")
    node(LinkTagScreenTestTags.CANCEL_BUTTON).assertDoesNotExist()
    click(LinkTagScreenTestTags.DONE_BUTTON)
    click(LinkTagScreenTestTags.BACK_BUTTON)

    assertEquals(listOf("done", "done"), calls)
  }

  @Test
  fun backCancelsBeforeTheTagIsLinked() {
    show(idle)

    click(LinkTagScreenTestTags.BACK_BUTTON)

    assertEquals(listOf("cancel"), calls)
  }
}
