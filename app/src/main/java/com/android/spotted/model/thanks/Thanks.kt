// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.thanks

/**
 * A "thank you" from the owner of a closed alert to a helper who sent a sighting on it. A helper is
 * thanked at most once per alert.
 *
 * @property alertId The closed alert the helper helped on.
 * @property ownerId The owner of that alert, who says thanks.
 * @property helperId The helper who is thanked.
 * @property sightingId A sighting [helperId] sent on [alertId]: it proves they helped.
 * @property petName Name of the found pet, so the helper sees what they are thanked for.
 * @property thankedAtMillis When the owner said thanks, in epoch milliseconds.
 */
data class Thanks(
    val alertId: String,
    val ownerId: String,
    val helperId: String,
    val sightingId: String,
    val petName: String,
    val thankedAtMillis: Long,
) {
  /** One id per (alert, helper), so a second thanks for the same pair is the same document. */
  val id: String
    get() = idOf(alertId, helperId)

  companion object {
    fun idOf(alertId: String, helperId: String): String = "${alertId}_$helperId"
  }
}

/** [helperId] was already thanked for the alert [alertId], so nothing new was stored. */
class AlreadyThankedException(val alertId: String, val helperId: String) :
    IllegalStateException("$helperId was already thanked for alert $alertId")
