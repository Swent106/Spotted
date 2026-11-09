// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.tag

/** What one tap did to the tag. [tagId] is the id the tag now holds in its Spotted URL. */
sealed interface TagWriteOutcome {
  val tagId: String

  /** The tag already held a Spotted URL: nothing was written, [tagId] was read from it. */
  data class AlreadySpotted(override val tagId: String) : TagWriteOutcome

  /** The Spotted URL of [tagId] (see [TagUri]) was written on the tag. */
  data class Written(override val tagId: String) : TagWriteOutcome
}

/** Why a tag could not be written. Each one can be retried with another tap or another tag. */
enum class TagWriteError {
  /** The tag left the phone before the write finished. */
  TAG_LOST,
  /** The tag is locked and cannot be written. */
  READ_ONLY,
  /** The URL does not fit in the tag's memory. */
  NOT_ENOUGH_SPACE,
  /** The tag is not an NFC Forum (NDEF) tag. */
  UNSUPPORTED,
  /** This phone has no NFC, or NFC is turned off. */
  NFC_UNAVAILABLE,
}

/** A failed write, with the [error] to show to the user. */
class TagWriteException(val error: TagWriteError, cause: Throwable? = null) :
    Exception("Could not write the tag: $error", cause)

/**
 * Writes the Spotted URL on NFC tags. The only way the rest of the app writes tags: no Android NFC
 * types leave the implementation, so callers receive a plain [TagWriteOutcome] and tests use a
 * fake.
 */
interface TagWriter {
  /**
   * Waits for the next tag tapped on the phone. A tag that already holds a Spotted URL is left
   * untouched ([TagWriteOutcome.AlreadySpotted]); any other tag gets the URL of [newTagId]
   * ([TagWriteOutcome.Written]). Fails with a [TagWriteException]. Cancel the call to stop waiting.
   *
   * Only one tag reader or writer can be active at a time on a screen.
   */
  suspend fun writeOnNextTap(newTagId: String): Result<TagWriteOutcome>
}
