// Portions of this code were generated with the help of Claude (Anthropic).
// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.report

import android.app.Application
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepositoryLocal
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertRepository
import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.ui.components.MapPickerTestTags
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for [ReportMissingContent], driven by hand-made states and recording callbacks, plus
 * [ReportMissingScreen] wired to a real [ReportMissingViewModel] with in-memory repositories.
 */
@RunWith(AndroidJUnit4::class)
class ReportMissingContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val milo =
      Pet(
          id = "milo",
          ownerId = "owner",
          name = "Milo",
          species = Species.CAT,
          breed = "Orange tabby",
      )
  private val luna = Pet(id = "luna", ownerId = "owner", name = "Luna", species = Species.CAT)
  private val parcBertrand = Location(latitude = 46.1925, longitude = 6.1580)
  private val ready =
      ReportMissingUiState(pets = listOf(milo), selectedPetId = "milo", location = parcBertrand)

  private fun petTag(id: String) = "${ReportMissingScreenTestTags.PET_OPTION_PREFIX}$id"

  /**
   * In-memory [AlertRepository]: the unit-test fake lives in `test/`, which `androidTest` can't
   * use.
   */
  private class InMemoryAlertRepository : AlertRepository {
    val published = mutableListOf<Alert>()

    override fun getNewId() = "alert-${published.size + 1}"

    override suspend fun publishAlert(alert: Alert): Result<Unit> {
      published += alert
      return Result.success(Unit)
    }

    override suspend fun getAlertById(id: String): Result<Alert?> =
        Result.success(published.find { it.id == id })

    override suspend fun getMyAlerts(ownerId: String) =
        Result.success(published.filter { it.ownerId == ownerId })

    // Not used in this test
    override suspend fun closeAlert(alertId: String): Result<Unit> = Result.success(Unit)

    override suspend fun updateLastSeen(alertId: String, location: Location, seenAtMillis: Long) =
        Result.success(false)

    override suspend fun getOpenAlertsNear(center: Location, radiusKm: Double) =
        Result.success(published.toList())

    override fun observeOpenAlertsNear(center: Location, radiusKm: Double) =
        flowOf(Result.success(published.toList()))

    override suspend fun getCachedOpenAlertsNear(
        center: Location,
        radiusKm: Double,
        limit: Int,
    ) = Result.success(published.take(limit))

    override suspend fun getOpenAlertsByOwner(ownerId: String) =
        Result.success(published.filter { it.ownerId == ownerId })

    override suspend fun deleteAlertsByOwner(ownerId: String): Result<Unit> {
      published.removeAll { it.ownerId == ownerId }
      return Result.success(Unit)
    }
  }

  @Test
  fun loadingPets_showsASpinnerAndNoEmptyMessage() {
    composeTestRule.setContent {
      ReportMissingContent(state = ReportMissingUiState(isLoadingPets = true))
    }

    composeTestRule
        .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(ReportMissingScreenTestTags.NO_PETS).assertDoesNotExist()
  }

  @Test
  fun petWithAPhoto_showsTheImage() {
    val miloWithPhoto = milo.copy(photoUrl = "https://example.com/milo.jpg")
    composeTestRule.setContent {
      ReportMissingContent(state = ReportMissingUiState(pets = listOf(miloWithPhoto)))
    }

    composeTestRule.onNodeWithContentDescription("Milo", useUnmergedTree = true).assertExists()
  }

  @Test
  fun screen_publishesThroughTheViewModelAndCallsOnPublished() {
    val auth = FakeAuthRepository()
    val owner = runBlocking { auth.signInWithGoogle("fake-token").getOrThrow() }
    val pets = PetRepositoryLocal()
    runBlocking { pets.addPet(milo.copy(ownerId = owner.uid)) }
    val alerts = InMemoryAlertRepository()
    val viewModel = ReportMissingViewModel(alerts, pets, auth, now = { 1_000L })
    var published = false
    composeTestRule.setContent {
      ReportMissingScreen(onBack = {}, onPublished = { published = true }, viewModel = viewModel)
    }

    // The owner's only pet is preselected once loaded; the location comes from the map.
    composeTestRule.waitUntil(5_000) { viewModel.uiState.value.selectedPetId == "milo" }
    composeTestRule.runOnIdle { viewModel.setLocation(parcBertrand) }
    composeTestRule
        .onNodeWithTag(ReportMissingScreenTestTags.PUBLISH_BUTTON)
        .performScrollTo()
        .performClick()

    composeTestRule.waitUntil(5_000) { published }
    assertEquals(listOf("milo"), alerts.published.map { it.petId })
  }

  @Test
  fun factory_createsTheViewModel() {
    val viewModel =
        ReportMissingViewModel.Factory.create(
            ReportMissingViewModel::class.java,
            MutableCreationExtras().apply {
              set(
                  ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY,
                  ApplicationProvider.getApplicationContext<Application>(),
              )
            },
        )

    assertNotNull(viewModel)
  }

  @Test
  fun showsPetsMapAndADisabledPublishButton() {
    composeTestRule.setContent {
      ReportMissingContent(state = ReportMissingUiState(pets = listOf(milo, luna)))
    }

    composeTestRule.onNodeWithTag(petTag("milo")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(petTag("luna")).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MapPickerTestTags.MAP).performScrollTo().assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(ReportMissingScreenTestTags.PUBLISH_BUTTON)
        .performScrollTo()
        .assertIsNotEnabled()
  }

  @Test
  fun tappingAPet_reportsItsId() {
    var selected: String? = null
    composeTestRule.setContent {
      ReportMissingContent(
          state = ReportMissingUiState(pets = listOf(milo, luna)),
          onPetSelected = { selected = it },
      )
    }

    composeTestRule.onNodeWithTag(petTag("luna")).performClick()

    assertEquals("luna", selected)
  }

  @Test
  fun petAndLocationChosen_publishIsEnabledAndReportsTheTap() {
    var published = false
    composeTestRule.setContent {
      ReportMissingContent(state = ready, onPublish = { published = true })
    }

    composeTestRule
        .onNodeWithTag(ReportMissingScreenTestTags.PUBLISH_BUTTON)
        .performScrollTo()
        .assertIsEnabled()
        .performClick()

    assertTrue(published)
  }

  @Test
  fun whilePublishing_showsTheLoaderAndDisablesTheButton() {
    composeTestRule.setContent { ReportMissingContent(state = ready.copy(isPublishing = true)) }

    composeTestRule
        .onNodeWithTag(ReportMissingScreenTestTags.PUBLISH_BUTTON)
        .performScrollTo()
        .assertIsNotEnabled()
    composeTestRule
        .onNodeWithTag(ReportMissingScreenTestTags.PUBLISH_LOADER, useUnmergedTree = true)
        .assertIsDisplayed()
  }

  @Test
  fun noPets_showsTheEmptyMessage() {
    composeTestRule.setContent { ReportMissingContent(state = ReportMissingUiState()) }

    composeTestRule.onNodeWithTag(ReportMissingScreenTestTags.NO_PETS).assertIsDisplayed()
  }

  @Test
  fun error_isShownAndOkDismissesIt() {
    var dismissed = false
    composeTestRule.setContent {
      ReportMissingContent(
          state = ready.copy(errorMessage = ReportMissingViewModel.PUBLISH_FAILED),
          onDismissError = { dismissed = true },
      )
    }

    composeTestRule
        .onNodeWithTag(ReportMissingScreenTestTags.ERROR_MESSAGE)
        .performScrollTo()
        .assertIsDisplayed()
    composeTestRule.onNodeWithText("OK").performClick()

    assertTrue(dismissed)
  }

  @Test
  fun petAlreadyMissing_isLabelledAndItsRadioIsDisabled() {
    composeTestRule.setContent {
      ReportMissingContent(
          state =
              ReportMissingUiState(
                  pets = listOf(milo, luna),
                  openAlertIdByPetId = mapOf("milo" to "alert-milo"),
              )
      )
    }

    composeTestRule
        .onNodeWithTag(
            "${ReportMissingScreenTestTags.ALREADY_MISSING_PREFIX}milo",
            useUnmergedTree = true,
        )
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag("${ReportMissingScreenTestTags.ALREADY_MISSING_PREFIX}luna")
        .assertDoesNotExist()
    composeTestRule.onNodeWithTag(ReportMissingScreenTestTags.ALL_PETS_MISSING).assertDoesNotExist()
  }

  @Test
  fun allPetsMissing_saysSoAndPublishIsDisabled() {
    composeTestRule.setContent {
      ReportMissingContent(
          state = ready.copy(openAlertIdByPetId = mapOf("milo" to "alert-milo")),
      )
    }

    composeTestRule.onNodeWithTag(ReportMissingScreenTestTags.ALL_PETS_MISSING).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(ReportMissingScreenTestTags.PUBLISH_BUTTON)
        .performScrollTo()
        .assertIsNotEnabled()
  }

  @Test
  fun alreadyMissingDialog_reportsOpenAndCancel() {
    var opened = false
    var dismissed = false
    composeTestRule.setContent {
      ReportMissingContent(
          state =
              ReportMissingUiState(
                  pets = listOf(milo),
                  openAlertIdByPetId = mapOf("milo" to "alert-milo"),
                  alreadyMissingPetId = "milo",
              ),
          onOpenExistingAlert = { opened = true },
          onDismissAlreadyMissing = { dismissed = true },
      )
    }

    composeTestRule.onNodeWithText("Milo is already reported missing").assertIsDisplayed()
    composeTestRule.onNodeWithTag(ReportMissingScreenTestTags.OPEN_ALERT_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(ReportMissingScreenTestTags.DISMISS_ALREADY_MISSING_BUTTON)
        .performClick()

    assertTrue(opened)
    assertTrue(dismissed)
  }

  @Test
  fun screen_tappingAPetAlreadyMissing_opensItsAlert() {
    val auth = FakeAuthRepository()
    val owner = runBlocking { auth.signInWithGoogle("fake-token").getOrThrow() }
    val pets = PetRepositoryLocal()
    runBlocking { pets.addPet(milo.copy(ownerId = owner.uid)) }
    val alerts = InMemoryAlertRepository()
    alerts.published +=
        Alert(
            id = "alert-milo",
            petId = "milo",
            ownerId = owner.uid,
            lastKnownLocation = parcBertrand,
            lostAtMillis = 500L,
            petName = "Milo",
            petSpecies = Species.CAT,
        )
    val viewModel = ReportMissingViewModel(alerts, pets, auth, now = { 1_000L })
    var openedAlertId: String? = null
    composeTestRule.setContent {
      ReportMissingScreen(
          onBack = {},
          onPublished = {},
          onOpenAlert = { openedAlertId = it },
          viewModel = viewModel,
      )
    }

    composeTestRule.waitUntil(5_000) { viewModel.uiState.value.allPetsMissing }
    composeTestRule.onNodeWithTag(petTag("milo")).performClick()
    composeTestRule.onNodeWithTag(ReportMissingScreenTestTags.OPEN_ALERT_BUTTON).performClick()

    composeTestRule.waitUntil(5_000) { openedAlertId == "alert-milo" }
  }

  @Test
  fun back_reportsTheTap() {
    var back = false
    composeTestRule.setContent {
      ReportMissingContent(state = ReportMissingUiState(), onBack = { back = true })
    }

    composeTestRule.onNodeWithTag(ReportMissingScreenTestTags.BACK_BUTTON).performClick()

    assertTrue(back)
  }
}
