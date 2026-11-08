// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.fakes

import com.android.spotted.model.tag.NfcAvailability
import com.android.spotted.model.tag.ScannedTag
import com.android.spotted.model.tag.TagReader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** [TagReader] driven by the test: no NFC hardware needed, works on the JVM and the emulator. */
class FakeTagReader(initialAvailability: NfcAvailability = NfcAvailability.AVAILABLE) : TagReader {
  private val availabilityState = MutableStateFlow(initialAvailability)
  private val scanEvents = MutableSharedFlow<ScannedTag>(extraBufferCapacity = 16)

  override val availability: Flow<NfcAvailability> = availabilityState
  override val scans: Flow<ScannedTag> = scanEvents

  /** How many collectors are reading tags right now: 1 while a screen scans, 0 otherwise. */
  val activeScanners: Int
    get() = scanEvents.subscriptionCount.value

  fun setAvailability(availability: NfcAvailability) {
    availabilityState.value = availability
  }

  /** Simulates a tap. Dropped if nobody is scanning, as with a real reader that is off. */
  fun emitScan(tag: ScannedTag) {
    scanEvents.tryEmit(tag)
  }
}
