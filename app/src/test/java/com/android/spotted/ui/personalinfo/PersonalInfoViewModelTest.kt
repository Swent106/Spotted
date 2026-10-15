package com.android.spotted.ui.personalinfo

import com.android.spotted.model.Location
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.model.auth.User
import com.android.spotted.model.fakes.FakeAddressRepository
import com.android.spotted.model.fakes.FakeLocationRepository
import com.android.spotted.model.location.LocationException
import com.android.spotted.model.user.FakeUserRepository
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
class PersonalInfoViewModelTest {
  private val testDispatcher = StandardTestDispatcher()
  private lateinit var userRepository: FakeUserRepository
  private lateinit var authRepository: FakeAuthRepository
  private lateinit var viewModel: PersonalInfoViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    userRepository = FakeUserRepository()
    authRepository = FakeAuthRepository()
    viewModel =
        PersonalInfoViewModel(
            user = User(uid = "user-1", email = "jamie@example.com"),
            userRepository = userRepository,
            authRepository = authRepository,
        )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun initialState_containsIdentityAndEmptyDashboardData() {
    val state = viewModel.uiState.value

    assertEquals("jamie@example.com", state.email)
    assertEquals(emptyList<String>(), state.petsBroughtHome)
    assertEquals(emptyList<String>(), state.achievements)
    assertEquals(0, state.helpersThisYear)
    assertFalse(state.canSave)
  }

  @Test
  fun signedOutUser_startsInSignedOutStateAndCannotSave() =
      runTest(testDispatcher) {
        val signedOutViewModel =
            PersonalInfoViewModel(
                user = null,
                userRepository = userRepository,
                authRepository = authRepository,
            )
        signedOutViewModel.onNameChanged("Jamie")
        signedOutViewModel.onHomeAreaChanged("Lausanne")

        assertTrue(signedOutViewModel.uiState.value.isSignedOut)
        assertFalse(signedOutViewModel.uiState.value.canSave)

        signedOutViewModel.saveProfile()
        advanceUntilIdle()

        assertEquals(0, userRepository.saveCalls)
      }

  @Test
  fun fieldChanges_areImmediatelyReflectedInState() {
    viewModel.onNameChanged("Jamie")
    viewModel.onPhoneChanged("+41 79 123 45 67")
    viewModel.onHomeAreaChanged("Lausanne")

    val state = viewModel.uiState.value
    assertEquals("Jamie", state.name)
    assertEquals("+41 79 123 45 67", state.phone)
    assertEquals("Lausanne", state.homeArea)
    assertTrue(state.canSave)
  }

  @Test
  fun firstAndLastNameChanges_combineIntoName() {
    viewModel.onFirstNameChanged(" Rayen ")
    viewModel.onLastNameChanged("Ben Ali")

    val state = viewModel.uiState.value
    assertEquals(" Rayen ", state.firstName)
    assertEquals("Ben Ali", state.lastName)
    assertEquals("Rayen Ben Ali", state.name)
  }

  @Test
  fun firstNameOnly_isEnoughForName() {
    viewModel.onFirstNameChanged("Rayen")
    viewModel.onHomeAreaChanged("Plainpalais, Geneva")

    assertEquals("Rayen", viewModel.uiState.value.name)
    assertTrue(viewModel.uiState.value.canSave)
  }

  @Test
  fun usernameChange_isReflectedInState() {
    viewModel.onUsernameChanged("@rayen.gva")

    assertEquals("@rayen.gva", viewModel.uiState.value.username)
  }

  @Test
  fun alertRadius_defaultsToThreeKm_andAcceptsOnlyListedOptions() {
    assertEquals(3, viewModel.uiState.value.alertRadiusKm)

    viewModel.onAlertRadiusChanged(10)
    assertEquals(10, viewModel.uiState.value.alertRadiusKm)

    viewModel.onAlertRadiusChanged(7)
    assertEquals(10, viewModel.uiState.value.alertRadiusKm)
  }

  @Test
  fun saveProfile_withMissingName_doesNotSave() =
      runTest(testDispatcher) {
        viewModel.onHomeAreaChanged("Lausanne")

        viewModel.saveProfile()
        advanceUntilIdle()

        assertTrue(userRepository.getUser("user-1").isFailure)
        assertFalse(viewModel.uiState.value.isSaved)
      }

  @Test
  fun saveProfile_withMissingHomeArea_doesNotSave() =
      runTest(testDispatcher) {
        viewModel.onNameChanged("Jamie")

        viewModel.saveProfile()
        advanceUntilIdle()

        assertTrue(userRepository.getUser("user-1").isFailure)
        assertFalse(viewModel.uiState.value.isSaved)
      }

  @Test
  fun saveProfile_withWhitespaceRequiredFields_doesNotSave() =
      runTest(testDispatcher) {
        viewModel.onNameChanged("   ")
        viewModel.onHomeAreaChanged("\t")

        assertFalse(viewModel.uiState.value.canSave)
        viewModel.saveProfile()
        advanceUntilIdle()

        assertTrue(userRepository.getUser("user-1").isFailure)
      }

  @Test
  fun saveProfile_withValidFieldsPersistsTrimmedProfile() =
      runTest(testDispatcher) {
        viewModel.onNameChanged(" Jamie ")
        viewModel.onPhoneChanged("+41 79 123 45 67")
        viewModel.onHomeAreaChanged(" Lausanne ")

        viewModel.saveProfile()
        advanceUntilIdle()

        val savedUser = userRepository.getUser("user-1").getOrThrow()
        assertEquals("Jamie", savedUser.name)
        assertEquals("Lausanne", savedUser.homeArea)
        assertEquals("jamie@example.com", savedUser.email)
        assertEquals("+41 79 123 45 67", savedUser.phone)
        assertTrue(viewModel.uiState.value.isSaved)
        assertFalse(viewModel.uiState.value.isSaving)
      }

  @Test
  fun saveProfile_doubleTapSavesOnlyOnce() =
      runTest(testDispatcher) {
        viewModel.onNameChanged("Jamie")
        viewModel.onHomeAreaChanged("Lausanne")

        viewModel.saveProfile()
        viewModel.saveProfile()
        advanceUntilIdle()

        assertEquals(1, userRepository.saveCalls)
        assertTrue(viewModel.uiState.value.isSaved)
      }

  @Test
  fun saveProfile_whenRepositoryFails_exposesError() =
      runTest(testDispatcher) {
        userRepository.failure = IllegalStateException("Save failed")
        viewModel.onNameChanged("Jamie")
        viewModel.onHomeAreaChanged("Lausanne")

        viewModel.saveProfile()
        advanceUntilIdle()

        assertEquals("Save failed", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isSaved)
        assertFalse(viewModel.uiState.value.isSaving)
      }

  @Test
  fun saveProfile_failureWithoutMessage_usesFallbackText() =
      runTest(testDispatcher) {
        userRepository.failure = IllegalStateException()
        viewModel.onNameChanged("Jamie")
        viewModel.onHomeAreaChanged("Lausanne")

        viewModel.saveProfile()
        advanceUntilIdle()

        assertEquals("Unable to save profile", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isSaved)
      }

  @Test
  fun homeLocation_isEmptyAtFirst_andFollowsThePlacedPin() {
    assertNull(viewModel.uiState.value.homeLocation)

    viewModel.onHomeLocationChanged(Location(46.52, 6.63))

    assertEquals(Location(46.52, 6.63), viewModel.uiState.value.homeLocation)
  }

  @Test
  fun onSavedHandled_resetsSavedFlag() =
      runTest(testDispatcher) {
        viewModel.onNameChanged("Jamie")
        viewModel.onHomeAreaChanged("Lausanne")
        viewModel.saveProfile()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSaved)

        viewModel.onSavedHandled()

        assertFalse(viewModel.uiState.value.isSaved)
      }

  @Test
  fun editingAfterSuccessfulSave_resetsSavedFlag() =
      runTest(testDispatcher) {
        viewModel.onNameChanged("Jamie")
        viewModel.onHomeAreaChanged("Lausanne")
        viewModel.saveProfile()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSaved)

        viewModel.onHomeAreaChanged("Geneva")

        assertEquals("Geneva", viewModel.uiState.value.homeArea)
        assertFalse(viewModel.uiState.value.isSaved)
      }

  @Test
  fun canSave_isFalseWhileLoggingOutOrLoggedOut() {
    val filled = PersonalInfoUiState(name = "Jamie", homeArea = "Lausanne")

    assertTrue(filled.canSave)
    assertFalse(filled.copy(isLoggingOut = true).canSave)
    assertFalse(filled.copy(isLoggedOut = true).canSave)
  }

  @Test
  fun canSave_staysTrueWithErrorMessage_soUserCanRetry() {
    val failed =
        PersonalInfoUiState(name = "Jamie", homeArea = "Lausanne", errorMessage = "Save failed")

    assertTrue(failed.canSave)
  }

  @Test
  fun saveProfile_afterLogout_doesNotSave() =
      runTest(testDispatcher) {
        viewModel.onNameChanged("Jamie")
        viewModel.onHomeAreaChanged("Lausanne")
        viewModel.logout()
        advanceUntilIdle()

        viewModel.saveProfile()
        advanceUntilIdle()

        assertTrue(userRepository.getUser("user-1").isFailure)
        assertFalse(viewModel.uiState.value.isSaved)
      }

  @Test
  fun logout_signsOutAndUpdatesState() =
      runTest(testDispatcher) {
        authRepository.signInWithGoogle("token")

        viewModel.logout()
        assertTrue(viewModel.uiState.value.isLoggingOut)
        advanceUntilIdle()

        assertNull(authRepository.currentUser.value)
        assertTrue(viewModel.uiState.value.isLoggedOut)
        assertFalse(viewModel.uiState.value.isLoggingOut)
      }

  @Test
  fun logout_whenRepositoryFails_exposesError() =
      runTest(testDispatcher) {
        val failedAuthRepository =
            object : AuthRepository by authRepository {
              override suspend fun signOut(): Result<Unit> {
                return Result.failure(IllegalStateException("Logout failed"))
              }
            }
        val failedViewModel =
            PersonalInfoViewModel(
                user = User(uid = "user-1", email = "jamie@example.com"),
                userRepository = userRepository,
                authRepository = failedAuthRepository,
            )

        failedViewModel.logout()
        advanceUntilIdle()

        assertEquals("Logout failed", failedViewModel.uiState.value.errorMessage)
        assertFalse(failedViewModel.uiState.value.isLoggedOut)
        assertFalse(failedViewModel.uiState.value.isLoggingOut)
      }

  @Test
  fun requestNearbyAlerts_setsAndCanClearRequest() {
    viewModel.requestNearbyAlerts()
    assertTrue(viewModel.uiState.value.nearbyAlertsRequested)

    viewModel.clearNearbyAlertsRequest()

    assertFalse(viewModel.uiState.value.nearbyAlertsRequested)
  }

  private fun viewModelWithLocation(
      locationRepository: FakeLocationRepository,
      addressRepository: FakeAddressRepository? = null,
  ) =
      PersonalInfoViewModel(
          user = User(uid = "user-1", email = "jamie@example.com"),
          userRepository = userRepository,
          authRepository = authRepository,
          locationRepository = locationRepository,
          addressRepository = addressRepository,
      )

  @Test
  fun useCurrentLocation_placesTheHomePinAtTheDevicePosition() =
      runTest(testDispatcher) {
        val gpsViewModel = viewModelWithLocation(FakeLocationRepository(Location(46.52, 6.63)))

        gpsViewModel.useCurrentLocation()
        advanceUntilIdle()

        assertEquals(Location(46.52, 6.63), gpsViewModel.uiState.value.homeLocation)
        assertNull(gpsViewModel.uiState.value.errorMessage)
      }

  @Test
  fun useCurrentLocation_writesTheAreaNameInTheHomeAreaField() =
      runTest(testDispatcher) {
        val gpsViewModel =
            viewModelWithLocation(
                FakeLocationRepository(),
                FakeAddressRepository("Ouchy, Lausanne"),
            )

        gpsViewModel.useCurrentLocation()
        advanceUntilIdle()

        assertEquals("Ouchy, Lausanne", gpsViewModel.uiState.value.homeArea)
      }

  @Test
  fun useCurrentLocation_whenNoPlaceNameIsFound_placesThePinAndKeepsTheTypedArea() =
      runTest(testDispatcher) {
        val addressRepository =
            FakeAddressRepository().apply { failure = NoSuchElementException("No place name") }
        val gpsViewModel = viewModelWithLocation(FakeLocationRepository(), addressRepository)
        gpsViewModel.onHomeAreaChanged("Flon")

        gpsViewModel.useCurrentLocation()
        advanceUntilIdle()

        assertEquals("Flon", gpsViewModel.uiState.value.homeArea)
        assertEquals(
            FakeLocationRepository.DEFAULT_LOCATION,
            gpsViewModel.uiState.value.homeLocation,
        )
      }

  @Test
  fun useCurrentLocation_whenNoPositionIsFound_keepsThePinAndShowsAnError() =
      runTest(testDispatcher) {
        val locationRepository =
            FakeLocationRepository().apply { failure = LocationException.Unavailable() }
        val gpsViewModel = viewModelWithLocation(locationRepository)

        gpsViewModel.useCurrentLocation()
        advanceUntilIdle()

        assertNull(gpsViewModel.uiState.value.homeLocation)
        assertEquals(
            PersonalInfoViewModel.LOCATION_UNAVAILABLE,
            gpsViewModel.uiState.value.errorMessage,
        )
      }

  @Test
  fun useCurrentLocation_withoutLocationRepository_doesNothing() =
      runTest(testDispatcher) {
        val before = viewModel.uiState.value

        viewModel.useCurrentLocation()
        advanceUntilIdle()

        assertEquals(before, viewModel.uiState.value)
      }

  @Test
  fun onLocationPermissionDenied_showsAnError() {
    viewModel.onLocationPermissionDenied()

    assertEquals(
        PersonalInfoViewModel.LOCATION_PERMISSION_DENIED,
        viewModel.uiState.value.errorMessage,
    )
  }
}
