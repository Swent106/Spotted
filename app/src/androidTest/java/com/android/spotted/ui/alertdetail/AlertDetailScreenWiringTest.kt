package com.android.spotted.ui.alertdetail

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** [AlertDetailScreen] wired to its ViewModel: loading the alert, Back and Share. */
@RunWith(AndroidJUnit4::class)
class AlertDetailScreenWiringTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val alert =
      Alert(
          id = "alert-1",
          petId = "pet-1",
          ownerId = "owner-1",
          lastKnownLocation = Location(46.5197, 6.6323),
          lostAtMillis = 1_700_000_000_000L,
          petName = "Luna",
          petSpecies = Species.CAT,
      )

  /** Keeps the activities the screen starts instead of starting them. */
  private class RecordingContext(base: Context) : ContextWrapper(base) {
    val started = mutableListOf<Intent>()

    override fun startActivity(intent: Intent) {
      started += intent
    }
  }

  private val context = RecordingContext(InstrumentationRegistry.getInstrumentation().targetContext)

  private fun setScreen(onBack: () -> Unit = {}) {
    val viewModel = AlertDetailViewModel(SingleAlertRepository(alert))
    composeTestRule.setContent {
      CompositionLocalProvider(LocalContext provides context) {
        AlertDetailScreen(
            viewModel = viewModel,
            alertId = alert.id,
            onReportSighting = {},
            onBack = onBack,
        )
      }
    }
    composeTestRule.waitUntil {
      composeTestRule
          .onAllNodes(hasTestTag(AlertDetailTestTags.NAME))
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
  }

  @Test
  fun backButton_callsOnBack() {
    var backs = 0
    setScreen(onBack = { backs++ })

    composeTestRule.onNodeWithTag(AlertDetailTestTags.BACK).performClick()

    assertEquals(1, backs)
  }

  @Test
  fun shareButton_opensTheShareSheetWithThePetAndPlace() {
    setScreen()

    composeTestRule.onNodeWithTag(AlertDetailTestTags.SHARE).performClick()
    composeTestRule.waitForIdle()

    val chooser = context.started.single()
    assertEquals(Intent.ACTION_CHOOSER, chooser.action)
    @Suppress("DEPRECATION") val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
    assertEquals(Intent.ACTION_SEND, send.action)
    assertEquals("text/plain", send.type)
    val text = send.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
    assertTrue(text, text.startsWith("Luna is missing near 46.5197, 6.6323."))
  }

  /**
   * Returns [alert] for its id; the unit-test fake lives in `test/`, which `androidTest` can't use.
   */
  private class SingleAlertRepository(private val alert: Alert) : AlertRepository {
    override fun getNewId() = "alert-2"

    override suspend fun publishAlert(alert: Alert): Result<Unit> = Result.success(Unit)

    override suspend fun getAlertById(id: String): Result<Alert?> =
        Result.success(alert.takeIf { it.id == id })

    override suspend fun getMyAlerts(ownerId: String) =
        Result.success(listOf(alert).filter { it.ownerId == ownerId })

    override suspend fun closeAlert(alertId: String): Result<Unit> = Result.success(Unit)

    override suspend fun getOpenAlertsNear(center: Location, radiusKm: Double) =
        Result.success(listOf(alert))

    override suspend fun getOpenAlertsByOwner(ownerId: String) =
        Result.success(listOf(alert).filter { it.ownerId == ownerId })

    override suspend fun deleteAlertsByOwner(ownerId: String): Result<Unit> = Result.success(Unit)
  }
}
