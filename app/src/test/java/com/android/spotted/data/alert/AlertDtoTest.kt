// Portions of this code were generated with the help of Copilot.
// Co-authored with Copilot.
package com.android.spotted.data.alert

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertDtoTest {

  @Test
  fun toDomain_mapsFieldsAndIgnoresUnknownBehaviors() {
    val dto =
        AlertDto(
            id = "alert-1",
            petId = "pet-1",
            ownerId = "owner-1",
            lastKnownLocation = LocationDto(latitude = 46.5, longitude = 6.6),
            lostAtMillis = 1_000L,
            closedAtMillis = 2_000L,
            status = AlertStatus.CLOSED.name,
            petName = "Milo",
            petSpecies = Species.CAT.name,
            petPhotoUrl = "https://example.test/milo.jpg",
            petAllergies = listOf("chicken"),
            petBehaviors =
                listOf(
                    Behavior.FEARFUL_OF_STRANGERS.name,
                    "UNKNOWN",
                    Behavior.FEARFUL_OF_STRANGERS.name,
                ),
        )

    assertEquals(
        Alert(
            id = "alert-1",
            petId = "pet-1",
            ownerId = "owner-1",
            lastKnownLocation = Location(latitude = 46.5, longitude = 6.6),
            lostAtMillis = 1_000L,
            closedAtMillis = 2_000L,
            status = AlertStatus.CLOSED,
            petName = "Milo",
            petSpecies = Species.CAT,
            petPhotoUrl = "https://example.test/milo.jpg",
            petAllergies = listOf("chicken"),
            petBehaviors = setOf(Behavior.FEARFUL_OF_STRANGERS),
        ),
        dto.toDomain(),
    )
  }

  @Test
  fun toDomain_returnsNullForUnknownStatus() {
    assertNull(AlertDto(status = "UNKNOWN").toDomain())
  }

  @Test
  fun toDomain_returnsNullForUnknownSpecies() {
    assertNull(AlertDto(petSpecies = "PARROT").toDomain())
  }

  @Test
  fun toDto_mapsAllFields() {
    val alert =
        Alert(
            id = "alert-1",
            petId = "pet-1",
            ownerId = "owner-1",
            lastKnownLocation = Location(latitude = 46.5, longitude = 6.6),
            lostAtMillis = 1_000L,
            closedAtMillis = 2_000L,
            status = AlertStatus.CLOSED,
            petName = "Milo",
            petSpecies = Species.CAT,
            petPhotoUrl = "https://example.test/milo.jpg",
            petAllergies = listOf("chicken"),
            petBehaviors = setOf(Behavior.DOESNT_LIKE_BEING_TOUCHED),
        )

    assertEquals(
        AlertDto(
            id = "alert-1",
            petId = "pet-1",
            ownerId = "owner-1",
            lastKnownLocation = LocationDto(latitude = 46.5, longitude = 6.6),
            lostAtMillis = 1_000L,
            closedAtMillis = 2_000L,
            status = AlertStatus.CLOSED.name,
            petName = "Milo",
            petSpecies = Species.CAT.name,
            petPhotoUrl = "https://example.test/milo.jpg",
            petAllergies = listOf("chicken"),
            petBehaviors = listOf(Behavior.DOESNT_LIKE_BEING_TOUCHED.name),
        ),
        alert.toDto(),
    )
  }
}
