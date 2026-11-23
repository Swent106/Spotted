// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.Pet

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PetAgeTest {

  private val zurich = ZoneId.of("Europe/Zurich")

  private fun millisAt(year: Int, month: Int, day: Int, hour: Int = 12) =
      ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zurich).toInstant().toEpochMilli()

  private val mid2026 = millisAt(2026, 6, 15)

  @Test
  fun knownBirthYear_givesTheDifferenceWithTheCurrentYear() {
    assertEquals(5, ageInYears(2021, mid2026, zurich))
    assertEquals(1, ageInYears(2025, mid2026, zurich))
  }

  @Test
  fun bornThisYear_isZero() {
    assertEquals(0, ageInYears(2026, mid2026, zurich))
  }

  @Test
  fun unknownOrFutureBirthYear_givesNoAge() {
    assertNull(ageInYears(null, mid2026, zurich))
    assertNull(ageInYears(2027, mid2026, zurich))
  }

  @Test
  fun theYearChangesAtTheDevicesNewYear() {
    // 23:30 on 31 December in Zurich is already 1 January in some time zones, not here
    assertEquals(5, ageInYears(2021, millisAt(2026, 12, 31, hour = 23), zurich))
    assertEquals(6, ageInYears(2021, millisAt(2027, 1, 1, hour = 0), zurich))
  }
}
