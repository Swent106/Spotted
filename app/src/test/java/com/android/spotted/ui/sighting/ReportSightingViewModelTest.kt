package com.android.spotted.ui.sighting

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.fakes.FakeSightingRepository
import com.android.spotted.model.photo.FakePhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportSightingViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()
  private lateinit var sightingRepository: FakeSightingRepository
  private lateinit var photoRepository: FakePhotoRepository
  private lateinit var viewModel: ReportSightingViewModel

  private val fixedTimestamp = 123456789L
  private val fixedReporterId = "test_reporter_123"

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    sightingRepository = FakeSightingRepository()
    photoRepository = FakePhotoRepository()
    viewModel =
        ReportSightingViewModel(
            sightingRepository = sightingRepository,
            photoRepository = photoRepository,
            now = { fixedTimestamp },
            reporterIdProvider = { fixedReporterId },
        )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun factory_executesFactory() {
    runCatching { ReportSightingViewModel.Factory.create(ReportSightingViewModel::class.java) }
  }

  @Test
  fun stateUpdates_workCorrectly() {
    viewModel.setSpecies(Species.CAT)
    assertEquals(Species.CAT, viewModel.uiState.value.selectedSpecies)

    val location = Location(46.5, 6.6)
    viewModel.setLocation(location, "Lausanne")
    assertEquals(location, viewModel.uiState.value.location)
    assertEquals("Lausanne", viewModel.uiState.value.locationAddress)

    viewModel.setPhotoUri("content://media/photo.jpg")
    assertEquals("content://media/photo.jpg", viewModel.uiState.value.photoUri)

    viewModel.selectAlert("alert_1")
    assertEquals("alert_1", viewModel.uiState.value.selectedAlertId)
    assertFalse(viewModel.uiState.value.isNoMatchingAlertSelected)

    viewModel.selectNoMatchingAlert()
    assertNull(viewModel.uiState.value.selectedAlertId)
    assertTrue(viewModel.uiState.value.isNoMatchingAlertSelected)

    viewModel.clearErrorMessage()
    assertNull(viewModel.uiState.value.errorMessage)
  }

  @Test
  fun submitSighting_success_storesSightingAndSetsSubmittedFlag() = runTest {
    viewModel.setSpecies(Species.DOG)
    viewModel.setLocation(Location(46.5197, 6.6323))
    viewModel.setPhotoUri("content://media/dog.jpg")
    viewModel.selectNoMatchingAlert()

    assertTrue(viewModel.uiState.value.canSubmit)

    viewModel.submitSighting()

    val state = viewModel.uiState.value
    assertTrue("Submitted flag should be true", state.submitted)
    assertFalse("isSubmitting should be false", state.isSubmitting)
    assertNull("errorMessage should be null", state.errorMessage)

    assertEquals(1, sightingRepository.reportedSightings.size)
    val saved = sightingRepository.reportedSightings.first()
    assertEquals(fixedReporterId, saved.reporterId)
    assertEquals(fixedTimestamp, saved.seenAtMillis)
    assertEquals(Species.DOG, saved.species)
    assertNull(saved.alertId)
    assertNotNull("Photo URL should be saved from photo repository", saved.photoUrl)
  }

  @Test
  fun submitSighting_validationError_whenSpeciesMissing() = runTest {
    // Location is set, but species is null
    viewModel.setLocation(Location(46.5, 6.6))
    viewModel.selectNoMatchingAlert()

    assertFalse(viewModel.uiState.value.canSubmit)

    viewModel.submitSighting()

    val state = viewModel.uiState.value
    assertFalse(state.submitted)
    assertNotNull(state.errorMessage)
    assertTrue(sightingRepository.reportedSightings.isEmpty())
  }

  @Test
  fun submitSighting_photoUploadFailure_setsErrorMessageAndPreservesForm() = runTest {
    viewModel.setSpecies(Species.CAT)
    viewModel.setLocation(Location(46.5, 6.6))
    viewModel.setPhotoUri("content://media/cat.jpg")
    viewModel.selectNoMatchingAlert()

    photoRepository.failure = Exception("Upload failed")

    viewModel.submitSighting()

    val state = viewModel.uiState.value
    assertFalse("submitted should be false", state.submitted)
    assertFalse("isSubmitting should be false", state.isSubmitting)
    assertEquals("Upload failed", state.errorMessage)

    // Form data preserved
    assertEquals(Species.CAT, state.selectedSpecies)
    assertEquals("content://media/cat.jpg", state.photoUri)
    assertTrue(sightingRepository.reportedSightings.isEmpty())
  }

  @Test
  fun submitSighting_sightingReportFailure_setsErrorMessageAndPreservesForm() = runTest {
    viewModel.setSpecies(Species.DOG)
    viewModel.setLocation(Location(46.5, 6.6))
    viewModel.selectNoMatchingAlert()

    sightingRepository.reportFailure = Exception("Database write error")

    viewModel.submitSighting()

    val state = viewModel.uiState.value
    assertFalse("submitted should be false", state.submitted)
    assertFalse("isSubmitting should be false", state.isSubmitting)
    assertEquals("Database write error", state.errorMessage)

    // Form data preserved
    assertEquals(Species.DOG, state.selectedSpecies)
  }
}
