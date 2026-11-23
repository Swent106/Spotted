// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.alert

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.PetSize
import com.android.spotted.model.Pet.Sex
import com.android.spotted.model.Pet.Species

enum class AlertStatus {
  OPEN,
  CLOSED,
}

/** How the pet got lost, as told by the owner. [UNKNOWN] when the owner did not say. */
enum class LossCause {
  /** Got out of the home, garden or yard on its own. */
  ESCAPED_FROM_HOME,
  /** Ran off or got separated from the owner during a walk. */
  DURING_A_WALK,
  /** Ran off because a noise frightened it (fireworks, storm, traffic...). */
  SCARED_BY_NOISE,
  /** Got lost while moving house or travelling. */
  DURING_MOVE_OR_TRIP,
  /** Taken by someone. */
  STOLEN,
  UNKNOWN,
}

/** The kind of place where the pet got lost. [UNKNOWN] when the owner did not say. */
enum class LossEnvironment {
  CITY_CENTRE,
  SUBURB,
  VILLAGE_OR_RURAL,
  FOREST_OR_MOUNTAIN,
  UNKNOWN,
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
    /** Optional, set when the alert is published. */
    val lossCause: LossCause = LossCause.UNKNOWN,
    /** Optional, set when the alert is published. */
    val lossEnvironment: LossEnvironment = LossEnvironment.UNKNOWN,
    // Copied from the pet when the alert is published: only the owner can read the pet
    val petBirthYear: Int? = null,
    val petSex: Sex = Sex.UNKNOWN,
    val petSize: PetSize = PetSize.UNKNOWN,
) {
  /** When the pet was last seen at [lastKnownLocation]: confirmed sighting, else when lost. */
  val lastSeenAt: Long
    get() = lastSeenAtMillis ?: lostAtMillis
}
