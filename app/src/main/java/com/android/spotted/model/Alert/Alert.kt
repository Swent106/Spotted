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
    val status: AlertStatus = AlertStatus.OPEN,
    val petName: String,
    val petSpecies: Species,
    val petPhotoUrl: String? = null,
    val petAllergies: List<String> = emptyList(),
    val petBehaviors: List<Behavior> = emptyList(),
)
