// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.alertdetail

import androidx.lifecycle.ViewModel
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Behavior
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertRepository
import com.android.spotted.model.alert.AlertStatus
import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.model.distanceKm
import com.android.spotted.model.fakes.FakeAddressRepository
import com.android.spotted.model.fakes.FakeAlertRepository
import com.android.spotted.model.fakes.FakeLocationRepository
import com.android.spotted.model.formatDistance
import com.android.spotted.model.location.LocationException
import com.android.spotted.model.user.FakeUserRepository
import com.android.spotted.model.user.UserProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class OtherViewModel : ViewModel()

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
  fun loadAlert_open_exposesAlertDetails() =
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
  fun loadAlert_closed_exposesClosedStatus() =
      runTest(testDispatcher) {
        val closedAlert =
            alert("alert-closed").copy(status = AlertStatus.CLOSED, closedAtMillis = 2L)
        alertRepository.publishAlert(closedAlert)
        val viewModel = AlertDetailViewModel(alertRepository)

        viewModel.loadAlert(closedAlert.id)
        advanceUntilIdle()

        assertEquals(AlertStatus.CLOSED, viewModel.uiState.value.alert?.status)
        assertEquals(2L, viewModel.uiState.value.alert?.closedAtMillis)
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

  @Test
  fun loadAlert_failureWithoutMessage_usesFallbackError() =
      runTest(testDispatcher) {
        alertRepository.failure = IllegalStateException()
        val viewModel = AlertDetailViewModel(alertRepository)

        viewModel.loadAlert("alert-1")
        advanceUntilIdle()

        assertEquals(AlertDetailViewModel.LOAD_FAILED, viewModel.uiState.value.errorMessage)
      }

  @Test
  fun loadAlert_secondRequest_cancelsPendingFirstRequest() =
      runTest(testDispatcher) {
        val firstRequestStarted = CompletableDeferred<Unit>()
        val pendingFirstResult = CompletableDeferred<Result<Alert?>>()
        val firstAlert = alert(id = "first")
        val secondAlert = alert(id = "second")
        val repository =
            object : AlertRepository by alertRepository {
              override suspend fun getAlertById(id: String): Result<Alert?> =
                  if (id == firstAlert.id) {
                    firstRequestStarted.complete(Unit)
                    pendingFirstResult.await()
                  } else {
                    Result.success(secondAlert)
                  }
            }
        val viewModel = AlertDetailViewModel(repository)

        viewModel.loadAlert(firstAlert.id)
        runCurrent()
        assertTrue(firstRequestStarted.isCompleted)

        viewModel.loadAlert(secondAlert.id)
        advanceUntilIdle()
        pendingFirstResult.complete(Result.success(firstAlert))
        advanceUntilIdle()

        assertEquals(secondAlert, viewModel.uiState.value.alert)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
      }

  // ===================== Distance to the alert =====================

  private val alertLocation = Location(latitude = 46.2, longitude = 6.1)
  private val device = Location(latitude = 46.2054, longitude = 6.1) // About 600 m away
  private val home = Location(latitude = 46.22, longitude = 6.1)

  private val authRepository = FakeAuthRepository()
  private val userRepository = FakeUserRepository()

  /** Signs a user in and saves their profile, with [homeLocation] as the home pin. */
  private fun signInWithHome(homeLocation: Location?, homeArea: String = "") = runBlocking {
    val user = authRepository.signInWithGoogle("token").getOrThrow()
    userRepository.saveUser(
        UserProfile(
            uid = user.uid,
            name = "Alice",
            email = user.email,
            homeArea = homeArea,
            homeLocation = homeLocation,
        )
    )
  }

  private fun viewModelWithLocation(
      locationRepository: FakeLocationRepository,
      addressRepository: FakeAddressRepository? = null,
  ) =
      AlertDetailViewModel(
          alertRepository = alertRepository,
          locationRepository = locationRepository,
          authRepository = authRepository,
          userRepository = userRepository,
          addressRepository = addressRepository,
      )

  @Test
  fun distance_isMeasuredFromTheDevicePosition() =
      runTest(testDispatcher) {
        signInWithHome(home)
        alertRepository.publishAlert(alert("alert-1"))
        val viewModel = viewModelWithLocation(FakeLocationRepository(location = device))

        viewModel.loadAlert("alert-1")
        advanceUntilIdle()

        assertEquals(
            formatDistance(device.distanceKm(alertLocation)),
            viewModel.uiState.value.distanceText,
        )
      }

  @Test
  fun distance_permissionDenied_isMeasuredFromTheHomePin() =
      runTest(testDispatcher) {
        signInWithHome(home)
        alertRepository.publishAlert(alert("alert-1"))
        val locationRepository =
            FakeLocationRepository().apply { failure = LocationException.PermissionDenied() }
        val viewModel = viewModelWithLocation(locationRepository)

        viewModel.loadAlert("alert-1")
        advanceUntilIdle()

        assertEquals(
            formatDistance(home.distanceKm(alertLocation)),
            viewModel.uiState.value.distanceText,
        )
      }

  @Test
  fun distance_noFix_isMeasuredFromTheHomeArea_whenThereIsNoPin() =
      runTest(testDispatcher) {
        signInWithHome(homeLocation = null, homeArea = "Plainpalais, Geneva")
        alertRepository.publishAlert(alert("alert-1"))
        val locationRepository =
            FakeLocationRepository().apply { failure = LocationException.Unavailable() }
        val viewModel =
            viewModelWithLocation(locationRepository, FakeAddressRepository(location = home))

        viewModel.loadAlert("alert-1")
        advanceUntilIdle()

        assertEquals(
            formatDistance(home.distanceKm(alertLocation)),
            viewModel.uiState.value.distanceText,
        )
      }

  @Test
  fun distance_noPositionAndNoHome_showsTheAlertWithoutDistance() =
      runTest(testDispatcher) {
        signInWithHome(homeLocation = null)
        val expectedAlert = alert("alert-1")
        alertRepository.publishAlert(expectedAlert)
        val locationRepository =
            FakeLocationRepository().apply { failure = LocationException.Unavailable() }
        val viewModel = viewModelWithLocation(locationRepository)

        viewModel.loadAlert("alert-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(expectedAlert, state.alert)
        assertNull(state.distanceText)
        assertNull(state.errorMessage)
      }

  /** Loads "alert-1" and returns the distance shown. */
  private fun TestScope.distanceShownBy(viewModel: AlertDetailViewModel): String? {
    runBlocking { alertRepository.publishAlert(alert("alert-1")) }
    viewModel.loadAlert("alert-1")
    advanceUntilIdle()
    return viewModel.uiState.value.distanceText
  }

  private fun noFix() = FakeLocationRepository().apply { failure = LocationException.Unavailable() }

  @Test
  fun distance_withoutLocationRepository_isMeasuredFromTheHomePin() =
      runTest(testDispatcher) {
        signInWithHome(home)
        val viewModel =
            AlertDetailViewModel(
                alertRepository = alertRepository,
                authRepository = authRepository,
                userRepository = userRepository,
            )

        assertEquals(formatDistance(home.distanceKm(alertLocation)), distanceShownBy(viewModel))
      }

  @Test
  fun distance_noFixAndNotSignedIn_isNotShown() =
      runTest(testDispatcher) { assertNull(distanceShownBy(viewModelWithLocation(noFix()))) }

  @Test
  fun distance_noFixAndNoUserRepositories_isNotShown() =
      runTest(testDispatcher) {
        signInWithHome(home)
        val viewModel =
            AlertDetailViewModel(alertRepository = alertRepository, locationRepository = noFix())

        assertNull(distanceShownBy(viewModel))
      }

  @Test
  fun distance_noFixAndNoUserRepository_isNotShown() =
      runTest(testDispatcher) {
        signInWithHome(home)
        val viewModel =
            AlertDetailViewModel(
                alertRepository = alertRepository,
                locationRepository = noFix(),
                authRepository = authRepository,
            )

        assertNull(distanceShownBy(viewModel))
      }

  @Test
  fun distance_noFixAndProfileNotFound_isNotShown() =
      runTest(testDispatcher) {
        signInWithHome(home)
        userRepository.failure = Exception("offline")

        assertNull(distanceShownBy(viewModelWithLocation(noFix())))
      }

  @Test
  fun distance_noFixAndNoAddressRepository_isNotShown_whenThereIsNoPin() =
      runTest(testDispatcher) {
        signInWithHome(homeLocation = null, homeArea = "Plainpalais, Geneva")

        assertNull(distanceShownBy(viewModelWithLocation(noFix())))
      }

  @Test
  fun distance_noFixAndHomeAreaNotFound_isNotShown() =
      runTest(testDispatcher) {
        signInWithHome(homeLocation = null, homeArea = "Nowhere")
        val addressRepository =
            FakeAddressRepository().apply { failure = NoSuchElementException("unknown place") }

        assertNull(distanceShownBy(viewModelWithLocation(noFix(), addressRepository)))
      }

  @Test
  fun factory_createUnsupportedClass_throwsIllegalArgumentException() {
    assertThrows(IllegalArgumentException::class.java) {
      AlertDetailViewModel.Factory.create(OtherViewModel::class.java)
    }
  }

  private fun alert(id: String) =
      Alert(
          id = id,
          petId = "pet-$id",
          ownerId = "owner-1",
          lastKnownLocation = Location(latitude = 46.2, longitude = 6.1),
          lostAtMillis = 1_700_000_000_000L,
          petName = "Luna",
          petSpecies = Species.CAT,
      )
}
