// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.myalert

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.sighting.LikelyArea
import com.android.spotted.model.sighting.Sighting
import com.android.spotted.model.sighting.SightingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for [MyAlertContent] with hand-built states. The map is replaced by an empty box that
 * records what it was asked to draw, so no OpenStreetMap view is needed.
 */
@RunWith(AndroidJUnit4::class)
class MyAlertContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  private var drawnMarkers: List<SightingMarker> = emptyList()
  private var drawnArea: LikelyArea? = null

  private fun setContent(
      state: MyAlertUiState,
      onConfirm: (String) -> Unit = {},
      onDismiss: (String) -> Unit = {},
      onMarkFound: () -> Unit = {},
      onBack: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      MyAlertContent(
          state = state,
          onBack = onBack,
          onConfirm = onConfirm,
          onDismiss = onDismiss,
          onMarkFound = onMarkFound,
          map = { markers, likelyArea, _, modifier ->
            drawnMarkers = markers
            drawnArea = likelyArea
            Box(modifier)
          },
      )
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

  @Test
  fun loading_showsTheSpinner() {
    setContent(MyAlertUiState(isLoading = true))

    node(MyAlertScreenTestTags.LOADING).assertIsDisplayed()
  }

  @Test
  fun error_showsTheMessage() {
    setContent(MyAlertUiState(errorMessage = "Only the owner can open this alert."))

    node(MyAlertScreenTestTags.ERROR)
        .assertIsDisplayed()
        .assertTextEquals("Only the owner can open this alert.")
  }

  @Test
  fun noSightings_showsTheEmptyStateAndTheMap() {
    setContent(MyAlertUiState(alert = MILO_ALERT))

    node(MyAlertScreenTestTags.MAP).assertIsDisplayed()
    node(MyAlertScreenTestTags.NO_SIGHTINGS).assertIsDisplayed()
    assertTrue(drawnMarkers.isEmpty())
  }

  @Test
  fun pendingSighting_canBeConfirmedOrDismissed() {
    val confirmed = mutableListOf<String>()
    val dismissed = mutableListOf<String>()
    setContent(
        MyAlertUiState(alert = MILO_ALERT, sightings = listOf(PENDING)),
        onConfirm = { confirmed += it },
        onDismiss = { dismissed += it },
    )

    node(MyAlertScreenTestTags.status(PENDING.id)).assertTextEquals("To review")
    node(MyAlertScreenTestTags.confirm(PENDING.id)).performScrollTo().performClick()
    node(MyAlertScreenTestTags.dismiss(PENDING.id)).performScrollTo().performClick()

    assertEquals(listOf(PENDING.id), confirmed)
    assertEquals(listOf(PENDING.id), dismissed)
  }

  @Test
  fun confirmedSighting_showsItsStatusWithoutReviewButtons() {
    setContent(MyAlertUiState(alert = MILO_ALERT, sightings = listOf(CONFIRMED)))

    node(MyAlertScreenTestTags.status(CONFIRMED.id)).assertTextEquals("Confirmed")
    node(MyAlertScreenTestTags.confirm(CONFIRMED.id)).assertDoesNotExist()
    node(MyAlertScreenTestTags.dismiss(CONFIRMED.id)).assertDoesNotExist()
  }

  @Test
  fun dismissedSighting_staysInTheListButIsHiddenFromTheMap() {
    setContent(
        MyAlertUiState(
            alert = MILO_ALERT,
            sightings = listOf(PENDING, DISMISSED, CONFIRMED),
            likelyArea = AREA,
        )
    )

    node(MyAlertScreenTestTags.sighting(DISMISSED.id)).performScrollTo().assertIsDisplayed()
    node(MyAlertScreenTestTags.status(DISMISSED.id)).assertTextEquals("Dismissed")
    // Numbers follow the list, so the confirmed sighting keeps number 3 on the map
    assertEquals(
        listOf(SightingMarker(1, PENDING.location), SightingMarker(3, CONFIRMED.location)),
        drawnMarkers,
    )
    assertEquals(AREA, drawnArea)
  }

  @Test
  fun petHome_closesTheAlertOnlyAfterConfirming() {
    var markedFound = false
    setContent(MyAlertUiState(alert = MILO_ALERT), onMarkFound = { markedFound = true })

    node(MyAlertScreenTestTags.PET_HOME_BUTTON).performScrollTo().performClick()
    node(MyAlertScreenTestTags.PET_HOME_CANCEL).performClick()
    assertFalse(markedFound)

    node(MyAlertScreenTestTags.PET_HOME_BUTTON).performScrollTo().performClick()
    node(MyAlertScreenTestTags.PET_HOME_CONFIRM).performClick()
    assertTrue(markedFound)
  }

  @Test
  fun back_reportsTheTap() {
    var back = false
    setContent(MyAlertUiState(alert = MILO_ALERT), onBack = { back = true })

    node(MyAlertScreenTestTags.BACK_BUTTON).performClick()

    assertTrue(back)
  }

  private companion object {
    val PARC = Location(latitude = 46.1925, longitude = 6.1580)
    val MILO_ALERT =
        Alert(
            id = "alert-1",
            petId = "milo",
            ownerId = "owner",
            lastKnownLocation = PARC,
            lostAtMillis = 1_700_000_000_000L,
            petName = "Milo",
            petSpecies = Species.CAT,
        )

    fun sighting(id: String, status: SightingStatus, seenAt: Long, lat: Double) =
        Sighting(
            id = id,
            reporterId = "helper",
            location = Location(latitude = lat, longitude = 6.1580),
            seenAtMillis = seenAt,
            species = Species.CAT,
            alertId = MILO_ALERT.id,
            status = status,
        )

    val PENDING = sighting("s-pending", SightingStatus.PENDING, 1_700_000_300_000L, 46.1930)
    val DISMISSED = sighting("s-dismissed", SightingStatus.DISMISSED, 1_700_000_200_000L, 46.1940)
    val CONFIRMED = sighting("s-confirmed", SightingStatus.CONFIRMED, 1_700_000_100_000L, 46.1950)
    val AREA = LikelyArea(center = Location(46.1945, 6.1580), radiusMeters = 400.0)
  }
}
