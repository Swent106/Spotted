// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.components

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.data.tag.NfcTagReader
import com.android.spotted.data.tag.UnsupportedTagReader
import com.android.spotted.model.fakes.FakeTagReader
import com.android.spotted.model.tag.NfcAvailability
import com.android.spotted.model.tag.ScannedTag
import com.android.spotted.model.tag.TagIdSource
import com.android.spotted.model.tag.TagReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NfcTagScanTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val owner = TestLifecycleOwner()
  private val firstTag = ScannedTag("abcd1234", TagIdSource.SPOTTED_URL)
  private val secondTag = ScannedTag("047F00FF", TagIdSource.HARDWARE_UID)

  private fun moveTo(state: Lifecycle.State) {
    composeTestRule.runOnIdle { owner.moveTo(state) }
    composeTestRule.waitForIdle()
  }

  @Test
  fun readerIsActiveOnlyWhileTheScreenIsResumed() {
    val reader = FakeTagReader()
    val received = mutableListOf<ScannedTag>()
    composeTestRule.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides owner) {
        NfcTagScanEffect(reader) { received += it }
      }
    }
    composeTestRule.waitForIdle()
    assertEquals(0, reader.activeScanners)

    moveTo(Lifecycle.State.RESUMED)
    assertEquals(1, reader.activeScanners)
    reader.emitScan(firstTag)
    composeTestRule.waitForIdle()
    assertEquals(listOf(firstTag), received)

    moveTo(Lifecycle.State.STARTED)
    assertEquals(0, reader.activeScanners)
    reader.emitScan(secondTag)
    composeTestRule.waitForIdle()
    assertEquals(listOf(firstTag), received)
  }

  @Test
  fun leavingTheScreenStopsTheReader() {
    val reader = FakeTagReader()
    var shown by mutableStateOf(true)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides owner) {
        if (shown) NfcTagScanEffect(reader) {}
      }
    }
    moveTo(Lifecycle.State.RESUMED)
    assertEquals(1, reader.activeScanners)

    composeTestRule.runOnIdle { shown = false }
    composeTestRule.waitForIdle()

    assertEquals(0, reader.activeScanners)
  }

  @Test
  fun availabilityStateFollowsTheReader() {
    val reader = FakeTagReader(NfcAvailability.DISABLED)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides owner) {
        val availability by rememberNfcAvailability(reader)
        Text(availability.name, Modifier.testTag(AVAILABILITY_TAG))
      }
    }
    moveTo(Lifecycle.State.RESUMED)
    composeTestRule.onNodeWithTag(AVAILABILITY_TAG).assertTextEquals("DISABLED")

    composeTestRule.runOnIdle { reader.setAvailability(NfcAvailability.AVAILABLE) }

    composeTestRule.onNodeWithTag(AVAILABILITY_TAG).assertTextEquals("AVAILABLE")
  }

  @Test
  fun withoutAnActivityTheReaderIsUnsupported() {
    lateinit var reader: TagReader
    composeTestRule.setContent {
      CompositionLocalProvider(LocalActivity provides null) { reader = rememberNfcTagReader() }
    }

    composeTestRule.runOnIdle { assertSame(UnsupportedTagReader, reader) }
  }

  @Test
  fun withAnActivityTheReaderUsesTheNfcHardware() {
    lateinit var reader: TagReader
    composeTestRule.setContent { reader = rememberNfcTagReader() }

    composeTestRule.runOnIdle { assertTrue(reader is NfcTagReader) }
  }

  private companion object {
    const val AVAILABILITY_TAG = "nfc_availability"
  }
}

/** A lifecycle the test moves by hand, starting at CREATED (not yet visible). */
private class TestLifecycleOwner : LifecycleOwner {
  private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.CREATED }

  override val lifecycle: Lifecycle
    get() = registry

  fun moveTo(state: Lifecycle.State) {
    registry.currentState = state
  }
}
