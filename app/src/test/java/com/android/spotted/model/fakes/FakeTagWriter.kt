// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.fakes

import com.android.spotted.model.tag.TagWriteError
import com.android.spotted.model.tag.TagWriteException
import com.android.spotted.model.tag.TagWriteOutcome
import com.android.spotted.model.tag.TagWriter

/** [TagWriter] driven by the test: each call is one tap on the tag the test describes. */
class FakeTagWriter : TagWriter {
  /** The id in the Spotted URL already on the next tag, or `null` for a blank tag. */
  var existingTagId: String? = null

  /** If set, the next taps fail with this error. */
  var error: TagWriteError? = null

  /** Every id written on a tag, in order. */
  val writtenIds = mutableListOf<String>()

  override suspend fun writeOnNextTap(newTagId: String): Result<TagWriteOutcome> {
    error?.let {
      return Result.failure(TagWriteException(it))
    }
    existingTagId?.let {
      return Result.success(TagWriteOutcome.AlreadySpotted(it))
    }
    writtenIds += newTagId
    return Result.success(TagWriteOutcome.Written(newTagId))
  }
}
