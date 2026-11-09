// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.tag

/**
 * The rules for linking a tag to a pet, once the tag has been written by a [TagWriter].
 *
 * Firestore, not the tag, decides whether a tag is taken: a tag whose Spotted URL points to an
 * existing `petTags` document is refused, while a tag whose URL points to nothing (an earlier link
 * that failed, or a tag that was unlinked) is reused with the id it already holds.
 */
class TagLinker(private val petTagRepository: PetTagRepository) {

  /** A new tag id, to pass to [TagWriter.writeOnNextTap]. */
  fun newTagId(): String = petTagRepository.getNewId()

  /**
   * Links the tag described by [write] to [petId]. A failed [write] links nothing and its failure
   * is returned as is. A tag already linked, to any pet, fails with [TagAlreadyLinkedException].
   */
  suspend fun link(write: Result<TagWriteOutcome>, petId: String): Result<Unit> {
    val outcome = write.getOrElse {
      return Result.failure(it)
    }
    return when (outcome) {
      is TagWriteOutcome.Written -> petTagRepository.linkTag(outcome.tagId, petId)
      is TagWriteOutcome.AlreadySpotted -> linkIfFree(outcome.tagId, petId)
    }
  }

  private suspend fun linkIfFree(tagId: String, petId: String): Result<Unit> {
    val existing =
        petTagRepository.getTag(tagId).getOrElse {
          return Result.failure(it)
        }
    if (existing != null) return Result.failure(TagAlreadyLinkedException(tagId))
    return petTagRepository.linkTag(tagId, petId)
  }
}
