// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.tag

import kotlinx.coroutines.flow.Flow

/** Whether this phone can read NFC tags right now. */
enum class NfcAvailability {
  /** NFC hardware present and turned on. */
  AVAILABLE,
  /** NFC hardware present but turned off in the system settings. */
  DISABLED,
  /** No NFC hardware on this phone (or no screen to read from). */
  UNSUPPORTED,
}

/** Where a [ScannedTag.tagId] came from. */
enum class TagIdSource {
  /** Read from the Spotted URL written on the tag (see [TagUri]). The normal case. */
  SPOTTED_URL,
  /** The chip's hardware UID, used only when the tag holds no Spotted URL (e.g. a blank tag). */
  HARDWARE_UID,
}

/** One tap of a tag on the phone, reduced to plain values. */
data class ScannedTag(val tagId: String, val source: TagIdSource)

/**
 * Reads NFC tags. The only way the rest of the app sees NFC: no Android NFC types leave the
 * implementation, so ViewModels receive plain [ScannedTag] values and tests use a fake.
 */
interface TagReader {
  /** The current [NfcAvailability], then every change (e.g. the user turns NFC on). */
  val availability: Flow<NfcAvailability>

  /**
   * Tags tapped on the phone. The reader is active only while this flow is collected, so collect it
   * only while the screen is resumed. Holding a tag still emits it once per tap.
   */
  val scans: Flow<ScannedTag>
}
