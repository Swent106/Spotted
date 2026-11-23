// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui

import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.model.fakes.FakeNetworkMonitor
import com.android.spotted.model.user.FakeUserRepository
import com.android.spotted.model.user.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class MainViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()
  private lateinit var authRepository: FakeAuthRepository
  private lateinit var userRepository: FakeUserRepository
  private lateinit var network: FakeNetworkMonitor

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    authRepository = FakeAuthRepository()
    userRepository = FakeUserRepository()
    network = FakeNetworkMonitor(online = true)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun factory_executesFactory() {
    runCatching { MainViewModel.Factory.create(MainViewModel::class.java) }
  }

  @Test
  fun whileTheProfileIsRead_startDestinationIsLoading() = runTest {
    authRepository.signInWithGoogle("fake_token")
    Dispatchers.setMain(StandardTestDispatcher())

    // The collection has not run yet: the start screen is still unknown
    val viewModel = MainViewModel(authRepository, userRepository, network)

    assertEquals(StartDestination.LOADING, viewModel.uiState.value.destination)
  }

  @Test
  fun whenNotSignedIn_startDestinationIsSignIn() = runTest {
    val viewModel = MainViewModel(authRepository, userRepository, network)

    assertEquals(StartDestination.SIGNIN, viewModel.uiState.value.destination)
  }

  @Test
  fun whenSignedInWithoutProfile_startDestinationIsPersonalInfo() = runTest {
    authRepository.signInWithGoogle("fake_token")

    val viewModel = MainViewModel(authRepository, userRepository, network)

    assertEquals(StartDestination.PERSONAL_INFO, viewModel.uiState.value.destination)
  }

  @Test
  fun whenSignedInWithProfile_startDestinationIsHome() = runTest {
    val signInResult = authRepository.signInWithGoogle("fake_token")
    val user = signInResult.getOrThrow()

    val profile =
        UserProfile(
            uid = user.uid,
            name = "Test User",
            phone = "1234567890",
            email = user.email,
            homeArea = "Lausanne",
        )
    userRepository.saveUser(profile)

    val viewModel = MainViewModel(authRepository, userRepository, network)

    assertEquals(StartDestination.HOME, viewModel.uiState.value.destination)
  }

  @Test
  fun beforeWatching_isOnlineAndNetworkIsNotWatched() {
    network.online = false

    val viewModel = MainViewModel(authRepository, userRepository, network)

    assertTrue(viewModel.uiState.value.isOnline)
    assertFalse(network.isWatched)
  }

  @Test
  fun whenWatchingOnline_isOnline() {
    val viewModel = MainViewModel(authRepository, userRepository, network)

    viewModel.startWatchingNetwork()

    assertTrue(viewModel.uiState.value.isOnline)
    assertTrue(network.isWatched)
  }

  @Test
  fun whenWatchingOffline_isOffline() {
    network.online = false
    val viewModel = MainViewModel(authRepository, userRepository, network)

    viewModel.startWatchingNetwork()

    assertFalse(viewModel.uiState.value.isOnline)
  }

  @Test
  fun whenConnectionDropsThenComesBack_isOnlineFollowsIt() {
    val viewModel = MainViewModel(authRepository, userRepository, network)
    viewModel.startWatchingNetwork()

    network.online = false
    assertFalse(viewModel.uiState.value.isOnline)

    network.online = true
    assertTrue(viewModel.uiState.value.isOnline)
  }

  @Test
  fun afterStopWatching_networkIsNotWatchedAndLastValueIsKept() {
    val viewModel = MainViewModel(authRepository, userRepository, network)
    viewModel.startWatchingNetwork()
    network.online = false

    viewModel.stopWatchingNetwork()
    network.online = true

    assertFalse(network.isWatched)
    assertFalse(viewModel.uiState.value.isOnline)
  }

  @Test
  fun whenWatchingStartsAgain_isOnlineIsTheCurrentValue() {
    val viewModel = MainViewModel(authRepository, userRepository, network)
    viewModel.startWatchingNetwork()
    viewModel.stopWatchingNetwork()
    network.online = false

    viewModel.startWatchingNetwork()

    assertFalse(viewModel.uiState.value.isOnline)
    assertTrue(network.isWatched)
  }

  @Test
  fun startingTwice_watchesOnlyOnce() {
    val viewModel = MainViewModel(authRepository, userRepository, network)

    viewModel.startWatchingNetwork()
    viewModel.startWatchingNetwork()
    viewModel.stopWatchingNetwork()

    assertFalse(network.isWatched)
  }

  @Test
  fun connectivityChanges_doNotChangeTheStartDestination() {
    val viewModel = MainViewModel(authRepository, userRepository, network)
    viewModel.startWatchingNetwork()

    network.online = false

    assertEquals(StartDestination.SIGNIN, viewModel.uiState.value.destination)
  }
}
