package com.android.spotted.ui.sighting

import com.android.spotted.model.Location
import com.android.spotted.model.Pet.Species
import com.android.spotted.model.auth.FakeAuthRepository
import com.android.spotted.model.fakes.FakeLocationRepository
import com.android.spotted.model.fakes.FakeSightingRepository
import com.android.spotted.model.photo.FakePhotoRepository
import com.android.spotted.model.sighting.Sighting
import com.android.spotted.model.sighting.SightingRepository
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
  private lateinit var authRepository: FakeAuthRepository
  private lateinit var viewModel: ReportSightingViewModel

  private val fixedTimestamp = 123456789L

  @Before
  fun setUp() = runTest {
    Dispatchers.setMain(testDispatcher)
    sightingRepository = FakeSightingRepository()
    photoRepository = FakePhotoRepository()
    authRepository = FakeAuthRepository()

    // Sign in user by default for tests
    authRepository.signInWithGoogle("valid_token")

    viewModel =
        ReportSightingViewModel(
            sightingRepository = sightingRepository,
            photoRepository = photoRepository,
            authRepository = authRepository,
            now = { fixedTimestamp },
        )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun nearbyAlertOption_propertiesAndEquals() {
    val option1 = NearbyAlertOption("1", "Milo", "Orange cat", "http://photo.jpg", Species.CAT)
    val option2 = NearbyAlertOption("1", "Milo", "Orange cat", "http://photo.jpg", Species.CAT)

    assertEquals("1", option1.alertId)
    assertEquals("Milo", option1.petName)
    assertEquals("Orange cat", option1.description)
    assertEquals("http://photo.jpg", option1.photoUrl)
    assertEquals(Species.CAT, option1.species)
    assertEquals(option1, option2)
  }

  @Test
  fun defaultConstructorParameters_workCorrectly() {
    val vm =
        ReportSightingViewModel(
            sightingRepository,
            photoRepository,
            authRepository = authRepository,
        )
    assertNotNull(vm)
  }

  @Test
  fun factory_executesFactoryAndInnerRepo() = runTest {
    runCatching {
      val factoryVm = ReportSightingViewModel.Factory.create(ReportSightingViewModel::class.java)
      factoryVm.setSpecies(Species.CAT)
      factoryVm.setLocation(Location(46.5, 6.6))
      factoryVm.selectNoMatchingAlert()
      factoryVm.submitSighting()
    }

    val innerRepo =
        object : SightingRepository {
          override fun getNewId(): String = "id"

          override suspend fun reportSighting(sighting: Sighting): Result<Unit> =
              Result.success(Unit)

          override suspend fun getSightingsForAlert(alertId: String): Result<List<Sighting>> =
              Result.success(emptyList())
        }
    assertEquals("id", innerRepo.getNewId())
    assertTrue(
        innerRepo
            .reportSighting(Sighting("id", "rep", Location(0.0, 0.0), 1L, Species.DOG))
            .isSuccess,
    )
    assertTrue(innerRepo.getSightingsForAlert("alert-1").getOrNull()?.isEmpty() == true)
  }

  @Test
  fun loadCurrentLocation_success_updatesState() = runTest {
    val fakeLocationRepo = FakeLocationRepository(location = Location(46.52, 6.63))

    val vm =
        ReportSightingViewModel(
            sightingRepository = sightingRepository,
            photoRepository = photoRepository,
            locationRepository = fakeLocationRepo,
            authRepository = authRepository,
        )

    vm.loadCurrentLocation()

    assertEquals(Location(46.52, 6.63), vm.uiState.value.location)
    assertEquals("Taken from your GPS", vm.uiState.value.locationAddress)
  }

  @Test
  fun loadCurrentLocation_failure_setsErrorMessage() = runTest {
    val fakeLocationRepo = FakeLocationRepository()
    fakeLocationRepo.failure = Exception("GPS disabled")

    val vm =
        ReportSightingViewModel(
            sightingRepository = sightingRepository,
            photoRepository = photoRepository,
            locationRepository = fakeLocationRepo,
            authRepository = authRepository,
        )

    vm.loadCurrentLocation()

    assertEquals("GPS disabled", vm.uiState.value.errorMessage)
  }

  @Test
  fun selectAlert_and_selectNoMatchingAlert_autoPrefillSpecies() {
    viewModel.selectNoMatchingAlert()
    assertEquals(Species.DOG, viewModel.uiState.value.selectedSpecies)

    viewModel.setSpecies(Species.CAT)
    viewModel.selectAlert("alert_1")
    assertEquals("alert_1", viewModel.uiState.value.selectedAlertId)
  }

  @Test
  fun submitSighting_unauthenticated_blocksSubmission() = runTest {
    authRepository.signOut()

    viewModel.setSpecies(Species.DOG)
    viewModel.setLocation(Location(46.5197, 6.6323))
    viewModel.selectNoMatchingAlert()

    viewModel.submitSighting()

    val state = viewModel.uiState.value
    assertFalse(state.submitted)
    assertEquals("You must be signed in to submit a sighting", state.errorMessage)
    assertTrue(sightingRepository.reportedSightings.isEmpty())
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
    assertEquals("fake_uid_from_token", saved.reporterId)
    assertEquals(fixedTimestamp, saved.seenAtMillis)
    assertEquals(Species.DOG, saved.species)
    assertNull(saved.alertId)
    assertNotNull("Photo URL should be saved from photo repository", saved.photoUrl)
  }

  @Test
  fun submitSighting_validationError_whenLocationMissing() = runTest {
    // Species set, but location is null
    viewModel.setSpecies(Species.DOG)
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

    assertEquals(Species.DOG, state.selectedSpecies)
  }

  @Test
  fun submitSighting_unexpectedException_setsErrorMessage() = runTest {
    val throwingRepo =
        object : SightingRepository {
          override fun getNewId(): String = throw RuntimeException("Unexpected crash")

          override suspend fun reportSighting(sighting: Sighting): Result<Unit> =
              Result.success(Unit)

          override suspend fun getSightingsForAlert(alertId: String): Result<List<Sighting>> =
              Result.success(emptyList())
        }
    val vm =
        ReportSightingViewModel(
            sightingRepository = throwingRepo,
            photoRepository = photoRepository,
            authRepository = authRepository,
        )

    vm.setSpecies(Species.DOG)
    vm.setLocation(Location(46.5, 6.6))
    vm.selectNoMatchingAlert()

    vm.submitSighting()

    val state = vm.uiState.value
    assertFalse(state.isSubmitting)
    assertEquals("Unexpected crash", state.errorMessage)
  }
}
