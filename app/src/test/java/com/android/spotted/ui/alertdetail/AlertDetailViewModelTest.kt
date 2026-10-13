package com.android.spotted.ui.alertdetail

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertStatus
import com.android.spotted.model.fakes.FakeAlertRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlertDetailViewModelTest {
  private val testDispatcher = StandardTestDispatcher()
  private lateinit var alertRepository: FakeAlertRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    alertRepository = FakeAlertRepository()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun loadAlert_found_exposesCopiedAlertDetails() =
      runTest(testDispatcher) {
        val expectedAlert =
            Alert(
                id = "alert-1",
                petId = "pet-1",
                ownerId = "owner-1",
                lastKnownLocation = Location(latitude = 46.2, longitude = 6.1),
                lostAtMillis = 1_700_000_000_000L,
                status = AlertStatus.OPEN,
                petName = "Luna",
                petSpecies = Species.CAT,
                petPhotoUrl = "https://example.com/luna.jpg",
                petAllergies = listOf("chicken"),
                petBehaviors = setOf(Behavior.FEARFUL_OF_STRANGERS),
            )
        alertRepository.publishAlert(expectedAlert)
        val viewModel = AlertDetailViewModel(alertRepository)

        viewModel.loadAlert(expectedAlert.id)

        assertTrue(viewModel.uiState.value.isLoading)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(expectedAlert, state.alert)
        assertFalse(state.isLoading)
        assertFalse(state.notFound)
        assertNull(state.errorMessage)
      }

  @Test
  fun loadAlert_notFound_setsNotFoundWithoutError() =
      runTest(testDispatcher) {
        val viewModel = AlertDetailViewModel(alertRepository)

        viewModel.loadAlert("missing")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.alert)
        assertFalse(state.isLoading)
        assertTrue(state.notFound)
        assertNull(state.errorMessage)
      }

  @Test
  fun loadAlert_failure_setsErrorAndDoesNotMarkNotFound() =
      runTest(testDispatcher) {
        alertRepository.failure = IllegalStateException("offline")
        val viewModel = AlertDetailViewModel(alertRepository)

        viewModel.loadAlert("alert-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.alert)
        assertFalse(state.isLoading)
        assertFalse(state.notFound)
        assertEquals("offline", state.errorMessage)
      }
}
