// Portions of this code were generated with the help of Copilot.
// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.fakes

import com.android.spotted.model.tag.PetTag
import com.android.spotted.model.tag.PetTagRepository
import com.android.spotted.model.tag.TagAlreadyLinkedException
import com.android.spotted.model.user.UserProfile

class FakePetTagRepository(
    private val ownerId: String = "owner-1",
    initialTags: List<PetTag> = emptyList(),
    private val now: () -> Long = { 0L },
    private val ownerProfile: UserProfile = UserProfile(uid = ownerId, name = "", email = ""),
) : PetTagRepository {
  private val tags = initialTags.associateByTo(linkedMapOf()) { it.tagId }

  private var lastId = 0

  var failure: Throwable? = null
  val linkedTags: List<PetTag>
    get() = tags.values.toList()

  /** Predictable ids in the real format: 32 uppercase hex characters (…0001, …0002, ...). */
  override fun getNewId(): String = "%032X".format(++lastId)

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
            showPhone = true,
            showEmail = true,
            contactPhone = ownerProfile.phone,
            contactEmail = ownerProfile.email,
        )
    return Result.success(Unit)
  }

  override suspend fun unlinkTag(tagId: String): Result<Unit> {
    failure?.let {
      return Result.failure(it)
    }
    val tag = tags[tagId] ?: return Result.success(Unit)
    if (tag.ownerId != ownerId) {
      return Result.failure(
          SecurityException("Tag $tagId is not owned by $ownerId")
      ) // i chose Security exception as it can be an attck/accident and it is unsecure to unlink a
      // tag not of his pet
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

  override suspend fun setVisibleContacts(
      tagId: String,
      showPhone: Boolean,
      showEmail: Boolean,
      phone: String?,
      email: String?,
  ): Result<Unit> {
    failure?.let {
      return Result.failure(it)
    }
    if (!showPhone && !showEmail) {
      return Result.failure(
          IllegalArgumentException("At least one contact method must remain visible")
      )
    }
    val tag =
        tags[tagId] ?: return Result.failure(NoSuchElementException("Tag $tagId was not found"))
    if (tag.ownerId != ownerId) {
      return Result.failure(SecurityException("Tag $tagId is not owned by $ownerId"))
    }
    val contactPhone = if (showPhone) phone else null
    val contactEmail = if (showEmail) email else null
    tags[tagId] =
        tag.copy(
            showPhone = showPhone,
            showEmail = showEmail,
            contactPhone = contactPhone,
            contactEmail = contactEmail,
        )
    return Result.success(Unit)
  }
}
