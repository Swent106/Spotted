// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.myalert

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.model.fakes.FakeAlertRepository
import com.android.spotted.model.fakes.FakeSightingRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** [MyAlertScreen] connects the ViewModel to the screen and leaves once the alert is closed. */
@RunWith(AndroidJUnit4::class)
class MyAlertScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val alertRepository = FakeAlertRepository()
  private val authRepository = FakeAuthRepository()
  private lateinit var viewModel: MyAlertViewModel
  private var backs = 0

  @Before
  fun setUp() = runBlocking {
    authRepository.signInWithGoogle("token")
    alertRepository.publishAlert(ALERT)
    viewModel =
        MyAlertViewModel(
            alertId = ALERT.id,
            alertRepository = alertRepository,
            sightingRepository = FakeSightingRepository(),
            authRepository = authRepository,
        )
    composeTestRule.setContent { MyAlertScreen(viewModel = viewModel, onBack = { backs++ }) }
    composeTestRule.waitForIdle()
  }

  @Test
  fun openAlert_staysOnTheScreen() {
    assertEquals(ALERT, viewModel.uiState.value.alert)
    assertEquals(0, backs)
  }

  @Test
  fun petHome_closesTheAlert_andGoesBack() {
    viewModel.markFound()
    composeTestRule.waitForIdle()

    assertEquals(true, viewModel.uiState.value.isClosed)
    assertEquals(1, backs)
  }

  private companion object {
    val ALERT =
        Alert(
            id = "alert-1",
            petId = "pet-1",
            // FakeAuthRepository signs in this user
            ownerId = "fake_uid_from_token",
            lastKnownLocation = Location(46.2, 6.1),
            lostAtMillis = 1_700_000_000_000L,
            petName = "Luna",
            petSpecies = Species.CAT,
        )
  }
}
