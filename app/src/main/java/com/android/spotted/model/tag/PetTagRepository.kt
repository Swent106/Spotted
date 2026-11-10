// Portions of this code were generated with the help of Copilot.
// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.tag

class TagAlreadyLinkedException(tagId: String) :
    IllegalStateException("Tag $tagId is already linked to another owner")

interface PetTagRepository {
  /**
   * A new random tag id, to write on a blank tag before linking it: 32 uppercase hexadecimal
   * characters, the format the `petTags` security rules accept.
   */
  fun getNewId(): String

  suspend fun linkTag(tagId: String, petId: String): Result<Unit>

  suspend fun unlinkTag(tagId: String): Result<Unit>

  suspend fun getTag(tagId: String): Result<PetTag?>

  suspend fun getTagForPet(petId: String): Result<PetTag?>

  suspend fun setVisibleContacts(
      tagId: String,
      showPhone: Boolean,
      showEmail: Boolean,
      phone: String?,
      email: String?,
  ): Result<Unit>
}
