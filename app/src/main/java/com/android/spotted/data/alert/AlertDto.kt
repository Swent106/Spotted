package com.android.spotted.data.alert

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert

internal data class LocationDto(val latitude: Double = 0.0, val longitude: Double = 0.0) {
  fun toDomain() = Location(latitude = latitude, longitude = longitude)
}

internal data class AlertDto(
    val id: String = "",
    val petId: String = "",
    val ownerId: String = "",
    val lastKnownLocation: LocationDto = LocationDto(),
    val lostAtMillis: Long = 0L,
    val petName: String = "",
    val petSpecies: String = Species.DOG.name,
    val status: String = "OPEN"
    // add the other fields of Alert here, as simple types (String, Long, List<String>...)
) {
  fun toDomain() =
      Alert(
          id = id,
          petId = petId,
          ownerId = ownerId,
          lastKnownLocation = lastKnownLocation.toDomain(),
          lostAtMillis = lostAtMillis,
          petName = petName,
          petSpecies = Species.valueOf(petSpecies))
}

internal fun Location.toDto() = LocationDto(latitude, longitude)

internal fun Alert.toDto() =
    AlertDto(
        id = id,
        petId = petId,
        ownerId = ownerId,
        lastKnownLocation = lastKnownLocation.toDto(),
        lostAtMillis = lostAtMillis,
        petName = petName,
        petSpecies = petSpecies.name)
