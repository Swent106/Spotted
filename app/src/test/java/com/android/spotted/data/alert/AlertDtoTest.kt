// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.alert

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.PetSize
import com.android.spotted.model.Pet.Sex
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.LossCause
import com.android.spotted.model.alert.LossEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AlertDtoTest {

  private val alert =
      Alert(
          id = "alert-1",
          petId = "pet-1",
          ownerId = "owner-1",
          lastKnownLocation = Location(latitude = 46.5, longitude = 6.6),
          lostAtMillis = 1_000L,
          petName = "Rex",
          petSpecies = Species.DOG,
      )

  @Test
  fun roundTrip_keepsEveryCauseAndEnvironment() {
    for (cause in LossCause.entries) {
      for (environment in LossEnvironment.entries) {
        val withLoss = alert.copy(lossCause = cause, lossEnvironment = environment)

        assertEquals(withLoss, withLoss.toDto().toDomain())
      }
    }
  }

  @Test
  fun toDto_storesTheEnumNames() {
    val dto =
        alert
            .copy(
                lossCause = LossCause.DURING_A_WALK,
                lossEnvironment = LossEnvironment.SUBURB,
            )
            .toDto()

    assertEquals("DURING_A_WALK", dto.lossCause)
    assertEquals("SUBURB", dto.lossEnvironment)
  }

  @Test
  fun toDomain_missingCause_readsAsUnknown() {
    val loaded = alert.toDto().copy(lossCause = null).toDomain()

    assertEquals(LossCause.UNKNOWN, loaded?.lossCause)
  }

  @Test
  fun toDomain_missingEnvironment_readsAsUnknown() {
    val loaded = alert.toDto().copy(lossEnvironment = null).toDomain()

    assertEquals(LossEnvironment.UNKNOWN, loaded?.lossEnvironment)
  }

  @Test
  fun toDomain_unknownCause_keepsTheAlert_withCauseUnknown() {
    val dto =
        alert
            .copy(lossEnvironment = LossEnvironment.SUBURB)
            .toDto()
            .copy(lossCause = "LOST_IN_SPACE")

    val loaded = dto.toDomain()

    assertNotNull("An unknown cause must not hide the alert", loaded)
    assertEquals(LossCause.UNKNOWN, loaded?.lossCause)
    assertEquals(LossEnvironment.SUBURB, loaded?.lossEnvironment)
  }

  @Test
  fun toDomain_unknownEnvironment_keepsTheAlert_withEnvironmentUnknown() {
    val dto = alert.copy(lossCause = LossCause.STOLEN).toDto().copy(lossEnvironment = "BEACH")

    val loaded = dto.toDomain()

    assertNotNull("An unknown environment must not hide the alert", loaded)
    assertEquals(LossEnvironment.UNKNOWN, loaded?.lossEnvironment)
    assertEquals(LossCause.STOLEN, loaded?.lossCause)
  }

  @Test
  fun newAlert_defaultsToUnknown() {
    assertEquals(LossCause.UNKNOWN, alert.lossCause)
    assertEquals(LossEnvironment.UNKNOWN, alert.lossEnvironment)
  }

  @Test
  fun roundTrip_keepsThePetDetails() {
    for (sex in Sex.entries) {
      for (size in PetSize.entries) {
        val withDetails = alert.copy(petBirthYear = 2019, petSex = sex, petSize = size)

        assertEquals(withDetails, withDetails.toDto().toDomain())
      }
    }
  }

  @Test
  fun toDto_storesThePetDetails() {
    val dto = alert.copy(petBirthYear = 2019, petSex = Sex.MALE, petSize = PetSize.LARGE).toDto()

    assertEquals(2019, dto.petBirthYear)
    assertEquals("MALE", dto.petSex)
    assertEquals("LARGE", dto.petSize)
  }

  @Test
  fun toDomain_alertWithoutPetDetails_readsUnknownAndNull() {
    val legacy = alert.toDto().copy(petBirthYear = null, petSex = null, petSize = null)

    val loaded = legacy.toDomain()

    assertNull(loaded?.petBirthYear)
    assertEquals(Sex.UNKNOWN, loaded?.petSex)
    assertEquals(PetSize.UNKNOWN, loaded?.petSize)
  }

  @Test
  fun toDomain_unknownPetSexOrSize_keepsTheAlert_withUnknown() {
    val loaded = alert.toDto().copy(petSex = "OTHER", petSize = "HUGE").toDomain()

    assertNotNull("An unknown sex or size must not hide the alert", loaded)
    assertEquals(Sex.UNKNOWN, loaded?.petSex)
    assertEquals(PetSize.UNKNOWN, loaded?.petSize)
  }
}
