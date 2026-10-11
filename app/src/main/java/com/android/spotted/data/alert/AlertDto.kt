package com.android.spotted.data.alert

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertStatus

internal data class LocationDto(val latitude: Double = 0.0, val longitude: Double = 0.0) {
  fun toDomain() = Location(latitude = latitude, longitude = longitude)
}

internal data class AlertDto(
    val id: String = "",
    val petId: String = "",
    val ownerId: String = "",
    val lastKnownLocation: LocationDto = LocationDto(),
    val lostAtMillis: Long = 0L,
    val status: String = AlertStatus.OPEN.name,
    val petName: String = "",
    val petSpecies: String = Species.DOG.name,
    val petPhotoUrl: String? = null,
    val petAllergies: List<String> = emptyList(),
    val petBehaviors: List<String> = emptyList()
) {
  fun toDomain(): Alert? {
    val alertStatus = runCatching { AlertStatus.valueOf(status) }.getOrNull() ?: return null
    val species = runCatching { Species.valueOf(petSpecies) }.getOrNull() ?: return null
    return Alert(
        id = id,
        petId = petId,
        ownerId = ownerId,
        lastKnownLocation = lastKnownLocation.toDomain(),
        lostAtMillis = lostAtMillis,
        status = alertStatus,
        petName = petName,
        petSpecies = species,
        petPhotoUrl = petPhotoUrl,
        petAllergies = petAllergies,
        petBehaviors =
            petBehaviors
                .mapNotNull { b -> runCatching { Behavior.valueOf(b) }.getOrNull() }
                .toSet())
  }
}

internal fun Location.toDto() = LocationDto(latitude, longitude)

internal fun Alert.toDto() =
    AlertDto(
        id = id,
        petId = petId,
        ownerId = ownerId,
        lastKnownLocation = lastKnownLocation.toDto(),
        lostAtMillis = lostAtMillis,
        status = status.name,
        petName = petName,
        petSpecies = petSpecies.name,
        petPhotoUrl = petPhotoUrl,
        petAllergies = petAllergies,
        petBehaviors = petBehaviors.map { it.name })
