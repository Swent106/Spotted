// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.vote

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.model.fakes.FakeSightingRepository
import com.android.spotted.model.fakes.FakeTrustRepository
import com.android.spotted.model.fakes.FakeVoteRepository
import com.android.spotted.model.sighting.Sighting
import com.android.spotted.model.sighting.SightingStatus
import com.android.spotted.model.trust.TrustScore
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoteViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private lateinit var authRepository: FakeAuthRepository
  private lateinit var voteRepository: FakeVoteRepository
  private lateinit var sightingRepository: FakeSightingRepository
  private lateinit var trustRepository: FakeTrustRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    authRepository = FakeAuthRepository()
    voteRepository = FakeVoteRepository()
    sightingRepository = FakeSightingRepository()
    trustRepository = FakeTrustRepository(sightings = sightingRepository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun vote_loadsCountAndCastsOneVote() =
      runTest(dispatcher) {
        signIn()
        val viewModel = eligibleViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.canVote)
        assertFalse(viewModel.uiState.value.alreadyVoted)
        assertEquals(0, viewModel.uiState.value.voteCount)

        viewModel.vote()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.alreadyVoted)
        assertFalse(viewModel.uiState.value.canVote)
        assertEquals(VoteViewModel.ALREADY_VOTED, viewModel.uiState.value.notEligibleReason)
        assertEquals(1, viewModel.uiState.value.voteCount)
      }

  @Test
  fun alreadyVoted_loadIsNotEligibleAndCannotVoteTwice() =
      runTest(dispatcher) {
        signIn()
        voteRepository.castVote(SIGHTING_ID, USER_ID).getOrThrow()
        val viewModel = eligibleViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canVote)
        assertTrue(viewModel.uiState.value.alreadyVoted)
        assertEquals(VoteViewModel.ALREADY_VOTED, viewModel.uiState.value.notEligibleReason)

        viewModel.vote()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.voteCount)
        assertEquals(1, voteRepository.getVoteCount(SIGHTING_ID).getOrThrow())
      }

  @Test
  fun reporterCannotVoteOnTheirOwnSighting() =
      runTest(dispatcher) {
        signIn()
        sightingRepository.reportSighting(sighting(reporterId = USER_ID))
        val viewModel =
            VoteViewModel(
                SIGHTING_ID,
                ALERT_ID,
                voteRepository,
                sightingRepository,
                trustRepository,
                authRepository,
            )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canVote)
        assertEquals(VoteViewModel.OWN_SIGHTING, viewModel.uiState.value.notEligibleReason)
      }

  @Test
  fun lowTrustUserIsNotEligible() =
      runTest(dispatcher) {
        signIn()
        val viewModel = eligibleViewModel(TrustScore(score = 0.39, judged = 3))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canVote)
        assertEquals(VoteViewModel.NOT_TRUSTED, viewModel.uiState.value.notEligibleReason)
      }

  @Test
  fun loadAndActionFailuresAreReported() =
      runTest(dispatcher) {
        signIn()
        sightingRepository.reportSighting(sighting())
        voteRepository.failure = IllegalStateException("network down")
        val viewModel = eligibleViewModel()
        advanceUntilIdle()

        assertEquals(VoteViewModel.LOAD_FAILED, viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.canVote)

        voteRepository.failure = null
        viewModel.load()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canVote)
        voteRepository.failure = IllegalStateException()
        viewModel.vote()
        advanceUntilIdle()

        assertEquals(VoteViewModel.VOTE_FAILED, viewModel.uiState.value.errorMessage)
      }

  @Test
  fun signedOutAndMissingSightingsAreNotEligible() =
      runTest(dispatcher) {
        val signedOut =
            VoteViewModel(
                SIGHTING_ID,
                ALERT_ID,
                voteRepository,
                sightingRepository,
                trustRepository,
                authRepository,
            )
        advanceUntilIdle()
        assertEquals(VoteViewModel.SIGN_IN_REQUIRED, signedOut.uiState.value.notEligibleReason)

        signIn()
        val missing =
            VoteViewModel(
                SIGHTING_ID,
                ALERT_ID,
                voteRepository,
                sightingRepository,
                trustRepository,
                authRepository,
            )
        advanceUntilIdle()
        assertEquals(VoteViewModel.SIGHTING_NOT_FOUND, missing.uiState.value.notEligibleReason)
      }

  private suspend fun signIn() {
    authRepository.signInWithGoogle("token")
  }

  private suspend fun eligibleViewModel(
      trust: TrustScore = TrustScore(score = 0.5, judged = 3),
  ): VoteViewModel {
    val sighting = sighting()
    sightingRepository.reportSighting(sighting)
    trustRepository.setTrust(USER_ID, trust)
    return VoteViewModel(
        SIGHTING_ID,
        ALERT_ID,
        voteRepository,
        sightingRepository,
        trustRepository,
        authRepository,
    )
  }

  private fun sighting(
      status: SightingStatus = SightingStatus.PENDING,
      reporterId: String = REPORTER_ID,
  ) =
      Sighting(
          id = SIGHTING_ID,
          reporterId = reporterId,
          location = Location(latitude = 46.2, longitude = 6.1),
          seenAtMillis = 1_700_000_000_000L,
          species = Species.CAT,
          alertId = ALERT_ID,
          status = status,
      )

  private companion object {
    const val USER_ID = "fake_uid_from_token"
    const val REPORTER_ID = "reporter-1"
    const val SIGHTING_ID = "sighting-1"
    const val ALERT_ID = "alert-1"
  }
}
