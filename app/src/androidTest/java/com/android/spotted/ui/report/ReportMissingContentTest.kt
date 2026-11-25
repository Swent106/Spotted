// Portions of this code were generated with the help of Claude (Anthropic).
// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.report

import android.app.Application
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Pet
import com.android.spotted.model.Pet.PetRepositoryLocal
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.alert.Alert
import com.android.spotted.model.alert.AlertRepository
import com.android.spotted.model.alert.LossCause
import com.android.spotted.model.alert.LossEnvironment
import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.ui.components.MapPickerTestTags
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    val (viewModel, alerts) = readyScreenViewModel()
    var published = false
    composeTestRule.setContent {
      ReportMissingScreen(onBack = {}, onPublished = { published = true }, viewModel = viewModel)
    }
    openWhatHappenedFromTheForm(viewModel)

    tap(ReportMissingScreenTestTags.WHAT_HAPPENED_PUBLISH)

    composeTestRule.waitUntil(5_000) { published }
    assertEquals(listOf("milo"), alerts.published.map { it.petId })
  }

  @Test
  fun screen_pickCauseAndPlace_thenPublish_sendsThemWithTheAlert() {
    val (viewModel, alerts) = readyScreenViewModel()
    var published = false
    composeTestRule.setContent {
      ReportMissingScreen(onBack = {}, onPublished = { published = true }, viewModel = viewModel)
    }
    openWhatHappenedFromTheForm(viewModel)

    tap(ReportMissingScreenTestTags.causeChip(LossCause.DURING_A_WALK))
    tap(ReportMissingScreenTestTags.environmentChip(LossEnvironment.SUBURB))
    tap(ReportMissingScreenTestTags.WHAT_HAPPENED_PUBLISH)

    composeTestRule.waitUntil(5_000) { published }
    val alert = alerts.published.single()
    assertEquals(LossCause.DURING_A_WALK, alert.lossCause)
    assertEquals(LossEnvironment.SUBURB, alert.lossEnvironment)
  }

  @Test
  fun screen_skip_publishesWithoutCauseOrPlace() {
    val (viewModel, alerts) = readyScreenViewModel()
    var published = false
    composeTestRule.setContent {
      ReportMissingScreen(onBack = {}, onPublished = { published = true }, viewModel = viewModel)
    }
    openWhatHappenedFromTheForm(viewModel)

    // A choice made before skipping is forgotten
    tap(ReportMissingScreenTestTags.causeChip(LossCause.STOLEN))
    tap(ReportMissingScreenTestTags.SKIP)

    composeTestRule.waitUntil(5_000) { published }
    val alert = alerts.published.single()
    assertEquals(LossCause.UNKNOWN, alert.lossCause)
    assertEquals(LossEnvironment.UNKNOWN, alert.lossEnvironment)
  }

  @Test
  fun screen_backFromWhatHappened_showsTheFormAgain_andKeepsTheChoice() {
    val (viewModel, _) = readyScreenViewModel()
    composeTestRule.setContent {
      ReportMissingScreen(onBack = {}, onPublished = {}, viewModel = viewModel)
    }
    openWhatHappenedFromTheForm(viewModel)
    tap(ReportMissingScreenTestTags.causeChip(LossCause.SCARED_BY_NOISE))
    tap(ReportMissingScreenTestTags.environmentChip(LossEnvironment.SUBURB))

    tap(ReportMissingScreenTestTags.WHAT_HAPPENED_BACK)

    composeTestRule.onNodeWithTag(ReportMissingScreenTestTags.NEXT_BUTTON).assertExists()
    composeTestRule.onNodeWithTag(ReportMissingScreenTestTags.WHAT_HAPPENED).assertDoesNotExist()
    assertEquals(LossCause.SCARED_BY_NOISE, viewModel.uiState.value.cause)
    assertEquals(LossEnvironment.SUBURB, viewModel.uiState.value.environment)
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
        .onNodeWithTag(ReportMissingScreenTestTags.NEXT_BUTTON)
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
  fun petAndLocationChosen_publishIsEnabledAndOpensWhatHappened() {
    var opened = false
    var published = false
    composeTestRule.setContent {
      ReportMissingContent(
          state = ready,
          onOpenWhatHappened = { opened = true },
          onPublish = { published = true },
      )
    }

    composeTestRule
        .onNodeWithTag(ReportMissingScreenTestTags.NEXT_BUTTON)
        .performScrollTo()
        .assertIsEnabled()
        .performClick()

    assertTrue(opened)
    // The alert is published from the "What happened?" step, not from the form
    assertFalse(published)
  }

  @Test
  fun whilePublishing_showsTheLoaderAndDisablesTheButton() {
    composeTestRule.setContent { ReportMissingContent(state = ready.copy(isPublishing = true)) }

    composeTestRule
        .onNodeWithTag(ReportMissingScreenTestTags.NEXT_BUTTON)
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
        .onNodeWithTag(ReportMissingScreenTestTags.NEXT_BUTTON)
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

  @Test
  fun whatHappened_showsEveryCauseAndPlace_butNotUnknown_andHidesTheForm() {
    composeTestRule.setContent { ReportMissingContent(state = whatHappened) }

    composeTestRule.onNodeWithTag(ReportMissingScreenTestTags.WHAT_HAPPENED).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ReportMissingScreenTestTags.NEXT_BUTTON).assertDoesNotExist()
    LossCause.entries.forEach { cause ->
      val chip = composeTestRule.onNodeWithTag(ReportMissingScreenTestTags.causeChip(cause))
      if (cause == LossCause.UNKNOWN) chip.assertDoesNotExist()
      else chip.performScrollTo().assertIsNotSelected()
    }
    LossEnvironment.entries.forEach { environment ->
      val chip =
          composeTestRule.onNodeWithTag(ReportMissingScreenTestTags.environmentChip(environment))
      if (environment == LossEnvironment.UNKNOWN) chip.assertDoesNotExist()
      else chip.performScrollTo().assertIsNotSelected()
    }
  }

  @Test
  fun whatHappened_tappingAChip_reportsTheTappedValue() {
    val causes = mutableListOf<LossCause>()
    val environments = mutableListOf<LossEnvironment>()
    composeTestRule.setContent {
      ReportMissingContent(
          state =
              whatHappened.copy(
                  cause = LossCause.STOLEN,
                  environment = LossEnvironment.CITY_CENTRE,
              ),
          onCauseToggled = { causes += it },
          onEnvironmentToggled = { environments += it },
      )
    }
    composeTestRule
        .onNodeWithTag(ReportMissingScreenTestTags.causeChip(LossCause.STOLEN))
        .assertIsSelected()

    tap(ReportMissingScreenTestTags.causeChip(LossCause.DURING_A_WALK))
    tap(ReportMissingScreenTestTags.causeChip(LossCause.STOLEN))
    tap(ReportMissingScreenTestTags.environmentChip(LossEnvironment.FOREST_OR_MOUNTAIN))
    tap(ReportMissingScreenTestTags.environmentChip(LossEnvironment.CITY_CENTRE))

    assertEquals(listOf(LossCause.DURING_A_WALK, LossCause.STOLEN), causes)
    assertEquals(
        listOf(LossEnvironment.FOREST_OR_MOUNTAIN, LossEnvironment.CITY_CENTRE),
        environments,
    )
  }

  @Test
  fun whatHappened_publishSkipAndBack_reportTheTaps() {
    var published = false
    var skipped = false
    var back = 0
    composeTestRule.setContent {
      ReportMissingContent(
          state = whatHappened,
          onPublish = { published = true },
          onSkip = { skipped = true },
          onBackToForm = { back++ },
      )
    }

    tap(ReportMissingScreenTestTags.WHAT_HAPPENED_PUBLISH)
    tap(ReportMissingScreenTestTags.SKIP)
    composeTestRule.onNodeWithTag(ReportMissingScreenTestTags.WHAT_HAPPENED_BACK).performClick()
    Espresso.pressBack()

    assertTrue(published)
    assertTrue(skipped)
    composeTestRule.runOnIdle { assertEquals("The arrow and the system back go back", 2, back) }
  }

  @Test
  fun whatHappened_whilePublishing_disablesPublishAndSkip() {
    composeTestRule.setContent {
      ReportMissingContent(state = whatHappened.copy(isPublishing = true))
    }

    composeTestRule
        .onNodeWithTag(ReportMissingScreenTestTags.WHAT_HAPPENED_PUBLISH)
        .performScrollTo()
        .assertIsNotEnabled()
    composeTestRule
        .onNodeWithTag(ReportMissingScreenTestTags.PUBLISH_LOADER, useUnmergedTree = true)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(ReportMissingScreenTestTags.SKIP)
        .performScrollTo()
        .assertIsNotEnabled()
  }

  @Test
  fun whatHappened_publishError_isShownOnTheStep() {
    var dismissed = false
    composeTestRule.setContent {
      ReportMissingContent(
          state = whatHappened.copy(errorMessage = "Could not publish"),
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

  private val whatHappened
    get() = ready.copy(showWhatHappened = true)

  private fun tap(tag: String) {
    composeTestRule.onNodeWithTag(tag).performScrollTo().performClick()
  }

  /** A real view model whose owner has only [milo], with the alerts it publishes. */
  private fun readyScreenViewModel(): Pair<ReportMissingViewModel, InMemoryAlertRepository> {
    val auth = FakeAuthRepository()
    val owner = runBlocking { auth.signInWithGoogle("fake-token").getOrThrow() }
    val pets = PetRepositoryLocal()
    runBlocking { pets.addPet(milo.copy(ownerId = owner.uid)) }
    val alerts = InMemoryAlertRepository()
    return ReportMissingViewModel(alerts, pets, auth, now = { 1_000L }) to alerts
  }

  /** Waits for [milo] to be preselected, sets the location and taps the form's publish button. */
  private fun openWhatHappenedFromTheForm(viewModel: ReportMissingViewModel) {
    composeTestRule.waitUntil(5_000) { viewModel.uiState.value.selectedPetId == "milo" }
    composeTestRule.runOnIdle { viewModel.setLocation(parcBertrand) }
    tap(ReportMissingScreenTestTags.NEXT_BUTTON)
    composeTestRule.onNodeWithTag(ReportMissingScreenTestTags.WHAT_HAPPENED).assertIsDisplayed()
  }
}
