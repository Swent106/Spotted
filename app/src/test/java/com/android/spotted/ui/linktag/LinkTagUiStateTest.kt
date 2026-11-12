// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.linktag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkTagUiStateTest {

  private val ready = LinkTagUiState(petName = "Rex")

  @Test
  fun defaultsShowBothContactsAndCannotStartBeforeThePetIsLoaded() {
    val state = LinkTagUiState()

    assertEquals(LinkTagStep.Idle, state.step)
    assertTrue(state.showPhone)
    assertTrue(state.showEmail)
    assertNull(state.errorMessage)
    assertFalse(state.canStart)
  }

  @Test
  fun canStartWhenIdleWithThePetLoaded() {
    assertTrue(ready.canStart)
  }

  @Test
  fun canRetryAfterAnError() {
    assertTrue(ready.copy(step = LinkTagStep.Error).canStart)
  }

  @Test
  fun cannotStartWhileLinkingOrOnceLinked() {
    listOf(LinkTagStep.WaitingForTag, LinkTagStep.Writing, LinkTagStep.Success).forEach { step ->
      assertFalse(step.name, ready.copy(step = step).canStart)
    }
  }

  @Test
  fun canStartWithOnlyOneContactShown() {
    assertTrue(ready.copy(showPhone = false).canStart)
    assertTrue(ready.copy(showEmail = false).canStart)
  }

  @Test
  fun cannotStartWithNoContactShown() {
    assertFalse(ready.copy(showPhone = false, showEmail = false).canStart)
  }

  @Test
  fun cannotStartWithABlankPetName() {
    assertFalse(ready.copy(petName = "  ").canStart)
  }
}
