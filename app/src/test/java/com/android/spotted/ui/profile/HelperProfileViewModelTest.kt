package com.android.spotted.ui.profile

import com.android.spotted.model.auth.AuthRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HelperProfileViewModelTest {

  private lateinit var userRepository: FakeUserRepository
  private lateinit var authRepository: FakeAuthRepository
  private lateinit var uid: String

  @Before
  fun setUp() = runTest {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    userRepository = FakeUserRepository()
    authRepository = FakeAuthRepository()
    uid = authRepository.signInWithGoogle("token").getOrThrow().uid
    userRepository.saveUser(
        UserProfile(uid = uid, name = "Rayen", email = "rayen@example.com", homeArea = "Lausanne")
    )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** A ViewModel as the screen shows it: loaded once. */
  private fun viewModel(auth: AuthRepository = authRepository) =
      HelperProfileViewModel(userRepository, auth).apply { load() }

  @Test
  fun load_showsTheNameAreaAndRadius() {
    val state = viewModel().uiState.value

    assertEquals("Rayen", state.name)
    assertEquals("Lausanne", state.homeArea)
    assertEquals("R", state.initial)
    assertFalse(state.isLoading)
  }

  @Test
  fun load_whenTheProfileCannotBeLoaded_showsAnError() {
    userRepository.failure = IllegalStateException("offline")

    assertEquals(HelperProfileViewModel.LOAD_FAILED, viewModel().uiState.value.errorMessage)
  }

  @Test
  fun badges_followThePetsBroughtHome() {
    assertFalse(HelperProfileUiState(petsBroughtHome = 0).hasFirstFind)
    assertTrue(HelperProfileUiState(petsBroughtHome = 1).hasFirstFind)
    assertFalse(HelperProfileUiState(petsBroughtHome = 4).hasFiveConfirmed)
    assertTrue(HelperProfileUiState(petsBroughtHome = 5).hasFiveConfirmed)
  }
}
