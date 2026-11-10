// Portions of this code were generated with the help of Copilot.
// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.tag

data class PetTag(
    val tagId: String,
    val petId: String,
    val ownerId: String,
    val linkedAtMillis: Long,
    val showPhone: Boolean = true,
    val showEmail: Boolean = true,
    val contactPhone: String? = null,
    val contactEmail: String? = null,
) {
  companion object {
    /** The tag id format `firestore.rules` accepts for `petTags/{tagId}`. Keep the two in sync. */
    private val ID = Regex("[0-9A-F]{1,32}")

    /**
     * True if [tagId] can be stored in Firestore. A tag whose Spotted URL holds any other id can
     * never be linked, so it is treated as a blank tag.
     */
    fun isValidId(tagId: String): Boolean = ID.matches(tagId)
  }
}
