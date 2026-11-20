// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.alert

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertLinkTest {

  private val milo =
      Alert(
          id = "milo-4F2A",
          petId = "pet-1",
          ownerId = "owner-1",
          lastKnownLocation = Location(46.5070, 6.6270),
          lostAtMillis = 0L,
          petName = "Milo",
          petSpecies = Species.CAT,
      )

  @Test
  fun alertLink_isTheSpottedAlertLink() {
    assertEquals("spotted://alert/milo-4F2A", alertLink("milo-4F2A"))
  }

  @Test
  fun alertIdFromLink_readsTheIdOfAnAlertLink() {
    assertEquals("milo-4F2A", alertIdFromLink("spotted://alert/milo-4F2A"))
    assertEquals("abc123", alertIdFromLink(alertLink("abc123")))
  }

  @Test
  fun alertIdFromLink_refusesAnythingElse() {
    listOf(
            null,
            "",
            "not a link",
            "https://alert/milo",
            "spotted://pet/milo",
            "spotted://alert",
            "spotted://alert/",
            "spotted://alert/milo/extra",
            "spotted://alert/milo%2Fhome",
            "spotted://alert/milo?x=1#y",
        )
        .forEach { link -> assertNull("$link", alertIdFromLink(link)) }
  }

  @Test
  fun alertShareText_namesThePlaceAndEndsWithTheLink() {
    assertEquals(
        "Milo is missing near Ouchy, Lausanne. Have you seen them? spotted://alert/milo-4F2A",
        alertShareText(milo, "Ouchy, Lausanne"),
    )
  }

  @Test
  fun alertShareText_withoutAPlace_usesTheCoordinates() {
    val expected =
        "Milo is missing near 46.5070, 6.6270. Have you seen them? spotted://alert/milo-4F2A"
    assertEquals(expected, alertShareText(milo, null))
    assertEquals(expected, alertShareText(milo, " "))
  }
}
