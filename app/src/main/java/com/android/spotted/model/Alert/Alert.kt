// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.alert

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.Species

enum class AlertStatus {
  OPEN,
  CLOSED,
}

data class Alert(
    val id: String,
    val petId: String,
    val ownerId: String,
    val lastKnownLocation: Location,
    val lostAtMillis: Long,
    val closedAtMillis: Long? = null,
    val status: AlertStatus = AlertStatus.OPEN,
    val petName: String,
    val petSpecies: Species,
    val petPhotoUrl: String? = null,
    val petAllergies: List<String> = emptyList(),
    val petBehaviors: Set<Behavior> = emptySet(),
    /**
     * When the pet was seen at [lastKnownLocation] by a confirmed sighting, or null while it is
     * still the place where it was lost.
     */
    val lastSeenAtMillis: Long? = null,
) {
  /** When the pet was last seen at [lastKnownLocation]: confirmed sighting, else when lost. */
  val lastSeenAt: Long
    get() = lastSeenAtMillis ?: lostAtMillis
}
