// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.notification

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertsToNotifyTest {

  @Test
  fun newAlert_isNotified() {
    assertEquals(listOf(alert("a1")), alertsToNotify(emptySet(), listOf(alert("a1")), ME))
  }

  @Test
  fun alreadyNotifiedAlert_isNotNotifiedAgain() {
    val current = listOf(alert("a1"), alert("a2"))

    assertEquals(listOf(alert("a2")), alertsToNotify(setOf("a1"), current, ME))
  }

  @Test
  fun ownAlert_isNeverNotified() {
    assertEquals(emptyList<Alert>(), alertsToNotify(emptySet(), listOf(alert("a1", ME)), ME))
  }

  @Test
  fun duplicates_areNotifiedOnce_inOrder() {
    val current = listOf(alert("a2"), alert("a1"), alert("a2"))

    assertEquals(listOf(alert("a2"), alert("a1")), alertsToNotify(emptySet(), current, ME))
  }

  private fun alert(id: String, ownerId: String = "someone") =
      Alert(
          id = id,
          petId = "pet-$id",
          ownerId = ownerId,
          lastKnownLocation = Location(46.52, 6.63),
          lostAtMillis = 0L,
          petName = "Milo",
          petSpecies = Species.DOG,
      )

  private companion object {
    const val ME = "me"
  }
}
