// Portions of this code were generated with the help of Copilot.
package com.android.spotted.model.sighting

import org.junit.Assert.assertEquals
import org.junit.Test

class CompassLabelTest {
  @Test
  fun compassLabel_usesEightPointBoundaries() {
    val cases =
        listOf(
            0 to "N",
            22 to "N",
            23 to "NE",
            67 to "NE",
            68 to "E",
            112 to "E",
            113 to "SE",
            157 to "SE",
            158 to "S",
            202 to "S",
            203 to "SW",
            247 to "SW",
            248 to "W",
            292 to "W",
            293 to "NW",
            337 to "NW",
            338 to "N",
            359 to "N",
        )

    cases.forEach { (degrees, expected) ->
      assertEquals("heading $degrees", expected, compassLabel(degrees))
    }
  }
}
