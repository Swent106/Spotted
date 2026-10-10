package com.android.spotted.ui.personalinfo

import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.model.auth.User
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
}
