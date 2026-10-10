package com.android.spotted.ui

import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.model.user.FakeUserRepository
import com.android.spotted.model.user.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()
  private lateinit var authRepository: FakeAuthRepository
  private lateinit var userRepository: FakeUserRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    authRepository = FakeAuthRepository()
    userRepository = FakeUserRepository()
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
  fun whenNotSignedIn_startDestinationIsSignIn() = runTest {
    val viewModel = MainViewModel(authRepository, userRepository)

    assertEquals(StartDestination.SIGNIN, viewModel.uiState.value)
  }

  @Test
  fun whenSignedInWithoutProfile_startDestinationIsPersonalInfo() = runTest {
    authRepository.signInWithGoogle("fake_token")

    val viewModel = MainViewModel(authRepository, userRepository)

    assertEquals(StartDestination.PERSONAL_INFO, viewModel.uiState.value)
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

    val viewModel = MainViewModel(authRepository, userRepository)

    assertEquals(StartDestination.HOME, viewModel.uiState.value)
  }
}
