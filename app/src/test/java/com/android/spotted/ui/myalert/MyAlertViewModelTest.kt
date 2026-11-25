// Portions of this code were generated with the help of Claude (Anthropic).
// Portions of this code were generated with the help of Copilot.
// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.myalert

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertStatus
import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.model.fakes.FakeAddressRepository
import com.android.spotted.model.fakes.FakeAlertRepository
import com.android.spotted.model.fakes.FakeSightingRepository
import com.android.spotted.model.fakes.FakeTrustRepository
import com.android.spotted.model.sighting.Sighting
import com.android.spotted.model.sighting.SightingStatus
import com.android.spotted.model.trust.TrustLevel
import com.android.spotted.model.trust.TrustScore
import com.android.spotted.model.trust.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MyAlertViewModelTest {
  private val testDispatcher = StandardTestDispatcher()
  private lateinit var alertRepository: FakeAlertRepository
  private lateinit var sightingRepository: FakeSightingRepository
  private lateinit var trustRepository: FakeTrustRepository
  private lateinit var authRepository: FakeAuthRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    alertRepository = FakeAlertRepository(now = { NOW })
    sightingRepository = FakeSightingRepository()
    trustRepository = FakeTrustRepository(sightings = sightingRepository)
    authRepository = FakeAuthRepository()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun loadAlert_ownerObservesLiveSightingsAndLikelyArea() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert()
        alertRepository.publishAlert(alert)
        val viewModel = viewModel(alert.id)
        advanceUntilIdle()

        assertEquals(alert, viewModel.uiState.value.alert)
        assertNull(viewModel.uiState.value.likelyArea)
        assertFalse(viewModel.uiState.value.isLoading)

        val sighting = sighting(status = SightingStatus.PENDING)
        sightingRepository.reportSighting(sighting)
        advanceUntilIdle()

        assertEquals(listOf(sighting), viewModel.uiState.value.sightings)
        assertNull(viewModel.uiState.value.likelyArea)

        viewModel.confirm(sighting.id)
        advanceUntilIdle()

        assertEquals(SightingStatus.CONFIRMED, viewModel.uiState.value.sightings.single().status)
        assertNotNull(viewModel.uiState.value.likelyArea)
      }

  @Test
  fun sightings_receiveTheirReportersTrustLevels() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert()
        val sightings =
            listOf(
                sighting(id = "new", status = SightingStatus.PENDING, reporterId = "new-reporter"),
                sighting(
                    id = "new-2",
                    status = SightingStatus.PENDING,
                    reporterId = "new-reporter",
                ),
                sighting(id = "low", status = SightingStatus.PENDING, reporterId = "low-reporter"),
                sighting(
                    id = "trusted",
                    status = SightingStatus.PENDING,
                    reporterId = "trusted-reporter",
                ),
                sighting(
                    id = "very-trusted",
                    status = SightingStatus.PENDING,
                    reporterId = "very-trusted-reporter",
                ),
            )
        trustRepository.setTrust("new-reporter", TrustScore(score = 0.9, judged = 2))
        trustRepository.setTrust("low-reporter", TrustScore(score = 0.39, judged = 3))
        trustRepository.setTrust("trusted-reporter", TrustScore(score = 0.4, judged = 3))
        trustRepository.setTrust("very-trusted-reporter", TrustScore(score = 0.76, judged = 3))
        alertRepository.publishAlert(alert)
        for (sighting in sightings) {
          sightingRepository.reportSighting(sighting)
        }
        val viewModel = viewModel(alert.id)

        advanceUntilIdle()

        assertEquals(
            mapOf(
                "new-reporter" to TrustLevel.NEW,
                "low-reporter" to TrustLevel.LOW,
                "trusted-reporter" to TrustLevel.TRUSTED,
                "very-trusted-reporter" to TrustLevel.VERY_TRUSTED,
            ),
            viewModel.uiState.value.reporterTrustLevels,
        )

        trustRepository.setTrust("new-reporter", TrustScore(score = 0.4, judged = 3))
        sightingRepository.setSightingStatus("new", SightingStatus.CONFIRMED).getOrThrow()
        advanceUntilIdle()

        assertEquals(
            TrustLevel.TRUSTED,
            viewModel.uiState.value.reporterTrustLevels["new-reporter"],
        )
      }

  @Test
  fun trustLoadFailure_keepsSightingsWithoutLevels() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert()
        val sighting = sighting(status = SightingStatus.PENDING)
        alertRepository.publishAlert(alert)
        sightingRepository.reportSighting(sighting)
        trustRepository.failure = IllegalStateException("offline")
        val viewModel = viewModel(alert.id)

        advanceUntilIdle()

        assertEquals(listOf(sighting), viewModel.uiState.value.sightings)
        assertTrue(viewModel.uiState.value.reporterTrustLevels.isEmpty())
        assertNull(viewModel.uiState.value.errorMessage)
      }

  @Test
  fun initialState_isLoadingUntilAlertLoaded() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert()
        alertRepository.publishAlert(alert)
        val viewModel = viewModel(alert.id)

        assertTrue(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.alert)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
      }

  @Test
  fun dismiss_updatesStatusToDismissed() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert()
        val sighting = sighting(status = SightingStatus.PENDING)
        alertRepository.publishAlert(alert)
        sightingRepository.reportSighting(sighting)
        val viewModel = viewModel(alert.id)
        advanceUntilIdle()

        viewModel.dismiss(sighting.id)
        advanceUntilIdle()

        assertEquals(SightingStatus.DISMISSED, viewModel.uiState.value.sightings.single().status)
      }

  @Test
  fun likelyArea_isNullAfterConfirmedSightingIsDismissed() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert()
        val sighting = sighting(status = SightingStatus.PENDING)
        alertRepository.publishAlert(alert)
        sightingRepository.reportSighting(sighting)
        val viewModel = viewModel(alert.id)
        advanceUntilIdle()

        viewModel.confirm(sighting.id)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.likelyArea)

        viewModel.dismiss(sighting.id)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.likelyArea)
      }

  @Test
  fun likelyArea_isNullWhenOnlyPendingSightings() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert()
        sightingRepository.reportSighting(sighting(status = SightingStatus.PENDING))
        alertRepository.publishAlert(alert)
        val viewModel = viewModel(alert.id)

        advanceUntilIdle()

        assertNull(viewModel.uiState.value.likelyArea)
      }

  @Test
  fun newSightingEmittedLater_isAddedWithoutReload() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert()
        val firstSighting = sighting(status = SightingStatus.PENDING)
        alertRepository.publishAlert(alert)
        sightingRepository.reportSighting(firstSighting)
        val viewModel = viewModel(alert.id)
        advanceUntilIdle()
        val secondSighting = sighting(id = "sighting-2", status = SightingStatus.PENDING)

        sightingRepository.reportSighting(secondSighting)
        advanceUntilIdle()

        assertEquals(
            setOf(firstSighting.id, secondSighting.id),
            viewModel.uiState.value.sightings.map { it.id }.toSet(),
        )
      }

  @Test
  fun statusFailure_errorMessageSurvivesNextSightingsEmission() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert()
        val firstSighting = sighting(status = SightingStatus.PENDING)
        alertRepository.publishAlert(alert)
        sightingRepository.reportSighting(firstSighting)
        val viewModel = viewModel(alert.id)
        advanceUntilIdle()
        sightingRepository.statusFailure = IllegalStateException("offline")

        viewModel.confirm(firstSighting.id)
        advanceUntilIdle()
        sightingRepository.reportSighting(
            sighting(id = "sighting-2", status = SightingStatus.PENDING)
        )
        advanceUntilIdle()

        assertEquals("offline", viewModel.uiState.value.errorMessage)
      }

  @Test
  fun alertNotFound_showsError() =
      runTest(testDispatcher) {
        signIn()
        val viewModel = viewModel("missing")

        advanceUntilIdle()

        assertEquals(MyAlertViewModel.ALERT_NOT_FOUND, viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.alert)
      }

  @Test
  fun alertRepositoryFailure_showsError() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert()
        alertRepository.publishAlert(alert)
        alertRepository.failure = IllegalStateException()
        val viewModel = viewModel(alert.id)

        advanceUntilIdle()

        assertEquals(MyAlertViewModel.LOAD_FAILED, viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.alert)
      }

  @Test
  fun sightingsObservationFailure_showsError() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert()
        alertRepository.publishAlert(alert)
        sightingRepository.observeFailure = IllegalStateException()
        val viewModel = viewModel(alert.id)

        advanceUntilIdle()

        assertEquals(
            MyAlertViewModel.SIGHTINGS_LOAD_FAILED,
            viewModel.uiState.value.errorMessage,
        )
      }

  @Test
  fun sightingsFailureWithMessage_isClearedBySuccessfulEmission() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert()
        alertRepository.publishAlert(alert)
        val viewModel = viewModel(alert.id)
        advanceUntilIdle()

        sightingRepository.emitObservationFailure(IllegalStateException("permission denied"))
        advanceUntilIdle()
        assertEquals("permission denied", viewModel.uiState.value.errorMessage)

        val sighting = sighting(status = SightingStatus.PENDING)
        sightingRepository.emitObservationSuccess(listOf(sighting))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(listOf(sighting), viewModel.uiState.value.sightings)
      }

  @Test
  fun sightingsFailureWithoutMessage_isClearedBySuccessfulEmission() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert()
        alertRepository.publishAlert(alert)
        val viewModel = viewModel(alert.id)
        advanceUntilIdle()

        sightingRepository.emitObservationFailure(IllegalStateException())
        advanceUntilIdle()
        assertEquals(
            MyAlertViewModel.SIGHTINGS_LOAD_FAILED,
            viewModel.uiState.value.errorMessage,
        )

        val sighting = sighting(status = SightingStatus.PENDING)
        sightingRepository.emitObservationSuccess(listOf(sighting))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(listOf(sighting), viewModel.uiState.value.sightings)
      }

  @Test
  fun sightingsRecovery_doesNotClearStatusUpdateError() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert()
        val sighting = sighting(status = SightingStatus.PENDING)
        alertRepository.publishAlert(alert)
        sightingRepository.reportSighting(sighting)
        val viewModel = viewModel(alert.id)
        advanceUntilIdle()
        sightingRepository.statusFailure = IllegalStateException("offline")

        viewModel.confirm(sighting.id)
        advanceUntilIdle()
        assertEquals("offline", viewModel.uiState.value.errorMessage)

        sightingRepository.emitObservationSuccess(listOf(sighting))
        advanceUntilIdle()

        assertEquals("offline", viewModel.uiState.value.errorMessage)
      }

  @Test
  fun markFoundFailure_showsErrorAndStaysOpen() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert()
        alertRepository.publishAlert(alert)
        val viewModel = viewModel(alert.id)
        advanceUntilIdle()
        alertRepository.failure = IllegalStateException("offline")

        viewModel.markFound()
        advanceUntilIdle()

        assertEquals("offline", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isClosed)
        assertEquals(AlertStatus.OPEN, viewModel.uiState.value.alert?.status)
      }

  @Test
  fun unauthenticatedUser_getsNotOwnerError() =
      runTest(testDispatcher) {
        val alert = alert()
        alertRepository.publishAlert(alert)
        val viewModel = viewModel(alert.id)

        advanceUntilIdle()

        assertEquals(MyAlertViewModel.NOT_OWNER, viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.alert)
      }

  @Test
  fun nonOwner_cannotConfirmDismissOrMarkFound() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert(ownerId = "someone-else")
        val sighting = sighting(status = SightingStatus.PENDING)
        alertRepository.publishAlert(alert)
        sightingRepository.reportSighting(sighting)
        val viewModel = viewModel(alert.id)
        advanceUntilIdle()

        viewModel.confirm(sighting.id)
        viewModel.dismiss(sighting.id)
        viewModel.markFound()
        advanceUntilIdle()

        assertEquals(SightingStatus.PENDING, sightingRepository.reportedSightings.single().status)
        assertEquals(AlertStatus.OPEN, alertRepository.publishedAlerts.single().status)
        assertFalse(viewModel.uiState.value.isClosed)
      }

  @Test
  fun closedAlert_setsIsClosedOnLoad() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert().copy(status = AlertStatus.CLOSED, closedAtMillis = NOW - 1)
        alertRepository.publishAlert(alert)
        val viewModel = viewModel(alert.id)

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isClosed)
        assertEquals(alert, viewModel.uiState.value.alert)
      }

  @Test
  fun closedAlert_ignoresConfirmDismissAndMarkFound() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert().copy(status = AlertStatus.CLOSED, closedAtMillis = NOW - 1)
        val sighting = sighting(status = SightingStatus.PENDING)
        alertRepository.publishAlert(alert)
        sightingRepository.reportSighting(sighting)
        val viewModel = viewModel(alert.id)
        advanceUntilIdle()

        viewModel.confirm(sighting.id)
        viewModel.dismiss(sighting.id)
        viewModel.markFound()
        advanceUntilIdle()

        assertEquals(SightingStatus.PENDING, sightingRepository.reportedSightings.single().status)
        assertEquals(alert, viewModel.uiState.value.alert)
        assertTrue(viewModel.uiState.value.isClosed)
      }

  @Test
  fun nonOwnerGetsErrorAndCannotLoadAlert() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert(ownerId = "someone-else")
        alertRepository.publishAlert(alert)
        val viewModel = viewModel(alert.id)

        advanceUntilIdle()

        assertNull(viewModel.uiState.value.alert)
        assertEquals(MyAlertViewModel.NOT_OWNER, viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.sightings.isEmpty())
      }

  @Test
  fun statusUpdateFailuresShowErrorWithoutChangingSightingStatuses() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert()
        alertRepository.publishAlert(alert)
        val pending = sighting(id = "pending", status = SightingStatus.PENDING)
        val confirmed = sighting(id = "confirmed", status = SightingStatus.CONFIRMED)
        sightingRepository.reportSighting(pending)
        sightingRepository.reportSighting(confirmed)
        val viewModel = viewModel(alert.id)
        advanceUntilIdle()
        sightingRepository.statusFailure = IllegalStateException("offline")

        viewModel.confirm(pending.id)
        advanceUntilIdle()

        assertEquals("offline", viewModel.uiState.value.errorMessage)
        assertEquals(
            SightingStatus.PENDING,
            viewModel.uiState.value.sightings.first { it.id == pending.id }.status,
        )

        viewModel.dismiss(confirmed.id)
        advanceUntilIdle()

        assertEquals("offline", viewModel.uiState.value.errorMessage)
        assertEquals(
            SightingStatus.CONFIRMED,
            viewModel.uiState.value.sightings.first { it.id == confirmed.id }.status,
        )
      }

  @Test
  fun markFoundClosesAlertAndSetsNavigationFlag() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert()
        alertRepository.publishAlert(alert)
        val viewModel = viewModel(alert.id)
        advanceUntilIdle()

        viewModel.markFound()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isClosed)
        assertEquals(AlertStatus.CLOSED, viewModel.uiState.value.alert?.status)
        assertEquals(NOW, viewModel.uiState.value.alert?.closedAtMillis)
      }

  @Test
  fun placeName_isWhereThePetWasLastSeen() =
      runTest(testDispatcher) {
        signIn()
        alertRepository.publishAlert(alert())
        val viewModel = viewModel("alert-1", FakeAddressRepository(name = "Plainpalais, Geneva"))
        advanceUntilIdle()

        assertEquals("Plainpalais, Geneva", viewModel.uiState.value.placeName)
      }

  @Test
  fun placeName_whenThePlaceCantBeNamed_isNull() =
      runTest(testDispatcher) {
        signIn()
        alertRepository.publishAlert(alert())
        val addressRepository =
            FakeAddressRepository().apply { failure = NoSuchElementException("no name") }
        val viewModel = viewModel("alert-1", addressRepository)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.placeName)
        assertEquals("alert-1", viewModel.uiState.value.alert?.id)
      }

  private suspend fun signIn() {
    authRepository.signInWithGoogle("token")
  }

  private fun viewModel(alertId: String, addressRepository: FakeAddressRepository? = null) =
      MyAlertViewModel(
          alertId = alertId,
          alertRepository = alertRepository,
          sightingRepository = sightingRepository,
          trustRepository = trustRepository,
          authRepository = authRepository,
          now = { NOW },
          addressRepository = addressRepository,
      )

  @Test
  fun confirm_dismiss_confirm_countsTheSightingOnceForTrust() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert()
        val sighting = sighting(status = SightingStatus.PENDING)
        alertRepository.publishAlert(alert)
        sightingRepository.reportSighting(sighting)
        val viewModel = viewModel(alert.id)
        advanceUntilIdle()

        viewModel.confirm(sighting.id)
        advanceUntilIdle()
        viewModel.dismiss(sighting.id)
        advanceUntilIdle()
        assertEquals(SightingStatus.DISMISSED, viewModel.uiState.value.sightings.single().status)
        viewModel.confirm(sighting.id)
        advanceUntilIdle()

        assertEquals(SightingStatus.CONFIRMED, viewModel.uiState.value.sightings.single().status)
        assertEquals(mapOf(sighting.id to Verdict.CONFIRMED), trustRepository.verdicts)
        assertEquals(
            TrustScore.INITIAL.after(Verdict.CONFIRMED),
            trustRepository.getTrust(sighting.reporterId).getOrThrow(),
        )
      }

  @Test
  fun dismiss_doesNotChangeTheReporterTrust() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert()
        val sighting = sighting(status = SightingStatus.PENDING)
        alertRepository.publishAlert(alert)
        sightingRepository.reportSighting(sighting)
        val viewModel = viewModel(alert.id)
        advanceUntilIdle()

        viewModel.dismiss(sighting.id)
        advanceUntilIdle()

        assertEquals(SightingStatus.DISMISSED, viewModel.uiState.value.sightings.single().status)
        assertTrue(trustRepository.verdicts.isEmpty())
        assertEquals(TrustScore.INITIAL, trustRepository.getTrust(sighting.reporterId).getOrThrow())
      }

  @Test
  fun dismiss_failureWithoutMessage_showsTheDismissError() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert()
        val sighting = sighting(status = SightingStatus.PENDING)
        alertRepository.publishAlert(alert)
        sightingRepository.reportSighting(sighting)
        val viewModel = viewModel(alert.id)
        advanceUntilIdle()
        sightingRepository.statusFailure = IllegalStateException()

        viewModel.dismiss(sighting.id)
        advanceUntilIdle()

        assertEquals(MyAlertViewModel.DISMISS_FAILED, viewModel.uiState.value.errorMessage)
        assertEquals(SightingStatus.PENDING, viewModel.uiState.value.sightings.single().status)
      }

  @Test
  fun confirm_trustFailure_showsErrorAndChangesNothing() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert()
        val sighting = sighting(status = SightingStatus.PENDING).copy(seenAtMillis = NOW + HOUR)
        alertRepository.publishAlert(alert)
        sightingRepository.reportSighting(sighting)
        val viewModel = viewModel(alert.id)
        advanceUntilIdle()
        trustRepository.failure = IllegalStateException()

        viewModel.confirm(sighting.id)
        advanceUntilIdle()

        assertEquals(MyAlertViewModel.CONFIRM_FAILED, viewModel.uiState.value.errorMessage)
        assertEquals(SightingStatus.PENDING, viewModel.uiState.value.sightings.single().status)
        assertEquals(alert, alertRepository.getAlertById(alert.id).getOrThrow())
        assertTrue(trustRepository.verdicts.isEmpty())
      }

  @Test
  fun confirm_newerSighting_movesTheAlertLastSeenPlace() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert()
        val sighting = sighting(status = SightingStatus.PENDING).copy(seenAtMillis = NOW + HOUR)
        alertRepository.publishAlert(alert)
        sightingRepository.reportSighting(sighting)
        val viewModel = viewModel(alert.id)
        advanceUntilIdle()

        viewModel.confirm(sighting.id)
        advanceUntilIdle()

        val moved = alert.copy(lastKnownLocation = sighting.location, lastSeenAtMillis = NOW + HOUR)
        assertEquals(moved, viewModel.uiState.value.alert)
        assertEquals(moved, alertRepository.getAlertById(alert.id).getOrThrow())
      }

  @Test
  fun confirm_olderSighting_keepsTheAlertLastSeenPlace() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert().copy(lastSeenAtMillis = NOW + 2 * HOUR)
        val sighting = sighting(status = SightingStatus.PENDING).copy(seenAtMillis = NOW + HOUR)
        alertRepository.publishAlert(alert)
        sightingRepository.reportSighting(sighting)
        val viewModel = viewModel(alert.id)
        advanceUntilIdle()

        viewModel.confirm(sighting.id)
        advanceUntilIdle()

        assertEquals(SightingStatus.CONFIRMED, viewModel.uiState.value.sightings.single().status)
        assertEquals(alert, viewModel.uiState.value.alert)
        assertEquals(alert, alertRepository.getAlertById(alert.id).getOrThrow())
      }

  @Test
  fun dismiss_newerSighting_keepsTheAlertLastSeenPlace() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert()
        val sighting = sighting(status = SightingStatus.PENDING).copy(seenAtMillis = NOW + HOUR)
        alertRepository.publishAlert(alert)
        sightingRepository.reportSighting(sighting)
        val viewModel = viewModel(alert.id)
        advanceUntilIdle()

        viewModel.dismiss(sighting.id)
        advanceUntilIdle()

        assertEquals(alert, alertRepository.getAlertById(alert.id).getOrThrow())
      }

  @Test
  fun confirm_statusFailure_doesNotMoveTheAlert() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert()
        val sighting = sighting(status = SightingStatus.PENDING).copy(seenAtMillis = NOW + HOUR)
        alertRepository.publishAlert(alert)
        sightingRepository.reportSighting(sighting)
        val viewModel = viewModel(alert.id)
        advanceUntilIdle()
        sightingRepository.statusFailure = IllegalStateException("offline")

        viewModel.confirm(sighting.id)
        advanceUntilIdle()

        assertEquals(alert, alertRepository.getAlertById(alert.id).getOrThrow())
        assertEquals("offline", viewModel.uiState.value.errorMessage)
      }

  @Test
  fun confirm_lastSeenFailure_showsError() =
      runTest(testDispatcher) {
        signIn()
        val alert = alert()
        val sighting = sighting(status = SightingStatus.PENDING).copy(seenAtMillis = NOW + HOUR)
        alertRepository.publishAlert(alert)
        sightingRepository.reportSighting(sighting)
        val viewModel = viewModel(alert.id)
        advanceUntilIdle()
        alertRepository.failure = IllegalStateException()

        viewModel.confirm(sighting.id)
        advanceUntilIdle()

        assertEquals(MyAlertViewModel.LAST_SEEN_UPDATE_FAILED, viewModel.uiState.value.errorMessage)
        assertEquals(alert, viewModel.uiState.value.alert)
      }

  private fun alert(ownerId: String = OWNER_ID) =
      Alert(
          id = "alert-1",
          petId = "pet-1",
          ownerId = ownerId,
          lastKnownLocation = Location(latitude = 46.2, longitude = 6.1),
          lostAtMillis = NOW,
          petName = "Luna",
          petSpecies = Species.CAT,
      )

  private fun sighting(
      id: String = "sighting-1",
      status: SightingStatus,
      reporterId: String = "reporter-1",
  ) =
      Sighting(
          id = id,
          reporterId = reporterId,
          location = Location(latitude = 46.21, longitude = 6.11),
          seenAtMillis = NOW,
          species = Species.CAT,
          alertId = "alert-1",
          status = status,
      )

  private companion object {
    const val OWNER_ID = "fake_uid_from_token"
    const val NOW = 1_700_000_000_000L
    const val HOUR = 60 * 60 * 1000L
  }
}
