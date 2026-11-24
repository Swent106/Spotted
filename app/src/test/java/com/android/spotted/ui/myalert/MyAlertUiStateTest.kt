// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.myalert

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.sighting.Sighting
import com.android.spotted.model.sighting.SightingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MyAlertUiStateTest {

  @Test
  fun mapSightings_hidesDismissedAndVotedFalse_keepsPendingAndConfirmed() {
    val pending = sighting("pending", SightingStatus.PENDING)
    val confirmed = sighting("confirmed", SightingStatus.CONFIRMED)
    val dismissed = sighting("dismissed", SightingStatus.DISMISSED)
    val votedFalse = sighting("voted-false", SightingStatus.VOTED_FALSE)

    val state = MyAlertUiState(sightings = listOf(pending, confirmed, dismissed, votedFalse))

    assertEquals(listOf(pending, confirmed), state.mapSightings)
  }

  private fun sighting(id: String, status: SightingStatus) =
      Sighting(
          id = id,
          reporterId = "helper",
          location = Location(46.52, 6.63),
          seenAtMillis = 1_000L,
          species = Species.DOG,
          alertId = "alert-1",
          status = status,
      )
}
