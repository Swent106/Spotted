// Portions of this code were generated with the help of Copilot.
package com.android.spotted.model.fakes

import com.android.spotted.model.tag.PetTag
import com.android.spotted.model.tag.PetTagRepository
import com.android.spotted.model.tag.TagAlreadyLinkedException

class FakePetTagRepository(
    private val ownerId: String = "owner-1",
    initialTags: List<PetTag> = emptyList(),
    private val now: () -> Long = { 0L },
) : PetTagRepository {
  private val tags = initialTags.associateByTo(linkedMapOf()) { it.tagId }

  var failure: Throwable? = null
  val linkedTags: List<PetTag>
    get() = tags.values.toList()

  override suspend fun linkTag(tagId: String, petId: String): Result<Unit> {
    failure?.let {
      return Result.failure(it)
    }
    val existingTag = tags[tagId]
    if (existingTag != null && existingTag.ownerId != ownerId) {
      return Result.failure(TagAlreadyLinkedException(tagId))
    }

    tags[tagId] =
        PetTag(
            tagId = tagId,
            petId = petId,
            ownerId = ownerId,
            linkedAtMillis = now(),
        )
    return Result.success(Unit)
  }

  override suspend fun unlinkTag(tagId: String): Result<Unit> {
    failure?.let {
      return Result.failure(it)
    }
    val tag = tags[tagId] ?: return Result.success(Unit)
    if (tag.ownerId != ownerId) {
      return Result.failure(SecurityException("Tag $tagId is not owned by $ownerId"))
    }
    tags.remove(tagId)
    return Result.success(Unit)
  }

  override suspend fun getTag(tagId: String): Result<PetTag?> {
    failure?.let {
      return Result.failure(it)
    }
    return Result.success(tags[tagId])
  }

  override suspend fun getTagForPet(petId: String): Result<PetTag?> {
    failure?.let {
      return Result.failure(it)
    }
    return Result.success(tags.values.firstOrNull { it.petId == petId })
  }
}
