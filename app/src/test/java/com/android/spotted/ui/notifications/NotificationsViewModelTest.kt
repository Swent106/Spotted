// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.notifications

import androidx.lifecycle.viewmodel.MutableCreationExtras
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.User
import com.android.spotted.model.fakes.FakeNotificationRepository
import com.android.spotted.model.notification.NotificationRepository
import com.android.spotted.model.notification.SightingNotification
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import org.junit.runner.RunWith
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NotificationsViewModelTest {

  private val auth = SwitchableAuthRepository()
  private val notifications = FakeNotificationRepository()

  @Before
  fun setUp() {
    // Unconfined: every emission of the fakes reaches the state before the next line runs
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel() = NotificationsViewModel(auth, notifications)

  @Test
  fun signedIn_isLoadingUntilTheRepositoryAnswers() {
    auth.currentUser.value = ALICE
    // A repository that hasn't emitted anything yet
    val silent =
        object : NotificationRepository {
          override fun observePendingSightings(ownerId: String) =
              MutableSharedFlow<Result<List<SightingNotification>>>()
        }

    val state = NotificationsViewModel(auth, silent).uiState.value

    assertTrue(state.isLoading)
    assertFalse(state.isEmpty)
  }

  @Test
  fun sameUidWithAnotherEmail_doesNotObserveAgainOrReset() {
    notifications.setPending(ALICE.uid, listOf(MILO_SEEN))
    auth.currentUser.value = ALICE
    val vm = viewModel()
    vm.open(MILO_SEEN)

    auth.currentUser.value = ALICE.copy(email = "alice@new.example.com")

    assertEquals(listOf(ALICE.uid), notifications.observedOwners)
    assertEquals(MILO_SEEN.alertId, vm.uiState.value.destinationAlertId)
  }

  /**
   * Regression (review of #333): with a real, queued dispatcher, an update for Alice already on its
   * way when the user switches to Bob must never reach Bob's state, even when Bob's own load fails
   * and the last list would otherwise be kept.
   */
  @Test
  fun queuedResultOfThePreviousUser_neverReachesTheNewOne() =
      runTest(StandardTestDispatcher()) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        notifications.setPending(ALICE.uid, listOf(MILO_SEEN))
        notifications.setFailure(BOB.uid, IllegalStateException("offline"))
        auth.currentUser.value = ALICE
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(listOf(MILO_SEEN), vm.uiState.value.items)

        // Both happen before any coroutine runs: Alice's update is queued, then the switch
        notifications.setPending(ALICE.uid, listOf(MILO_SEEN, MILO_SEEN_AGAIN))
        auth.currentUser.value = BOB
        advanceUntilIdle()

        assertTrue(vm.uiState.value.items.isEmpty())
        assertEquals(NotificationsViewModel.LOAD_FAILED, vm.uiState.value.errorMessage)
      }

  @Test
  fun queuedResultOfThePreviousUser_isDroppedOnSignOut() =
      runTest(StandardTestDispatcher()) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        notifications.setPending(ALICE.uid, listOf(MILO_SEEN))
        auth.currentUser.value = ALICE
        val vm = viewModel()
        advanceUntilIdle()

        notifications.setPending(ALICE.uid, listOf(MILO_SEEN, MILO_SEEN_AGAIN))
        auth.currentUser.value = null
        advanceUntilIdle()

        assertTrue(vm.uiState.value.items.isEmpty())
        assertFalse(vm.uiState.value.isLoading)
      }

  @Test
  fun signedOut_showsAnEmptyListWithoutLoading() {
    val state = viewModel().uiState.value

    assertFalse(state.isLoading)
    assertTrue(state.items.isEmpty())
    assertTrue(state.isEmpty)
    assertTrue(notifications.observedOwners.isEmpty())
  }

  @Test
  fun signedIn_showsTheOwnersPendingSightings() {
    notifications.setPending(ALICE.uid, listOf(MILO_SEEN))
    auth.currentUser.value = ALICE

    val state = viewModel().uiState.value

    assertEquals(listOf(MILO_SEEN), state.items)
    assertFalse(state.isLoading)
    assertNull(state.errorMessage)
    assertFalse(state.isEmpty)
    assertEquals(listOf(ALICE.uid), notifications.observedOwners)
  }

  @Test
  fun nothingPending_isEmpty() {
    auth.currentUser.value = ALICE

    val state = viewModel().uiState.value

    assertTrue(state.items.isEmpty())
    assertTrue(state.isEmpty)
  }

  @Test
  fun newSighting_appearsLive() {
    auth.currentUser.value = ALICE
    val vm = viewModel()

    notifications.setPending(ALICE.uid, listOf(MILO_SEEN))

    assertEquals(listOf(MILO_SEEN), vm.uiState.value.items)
  }

  @Test
  fun failure_showsAFixedMessageNotTheExceptionText() {
    auth.currentUser.value = ALICE
    notifications.setFailure(ALICE.uid, IllegalStateException("PERMISSION_DENIED: secret"))

    val state = viewModel().uiState.value

    assertEquals(NotificationsViewModel.LOAD_FAILED, state.errorMessage)
    assertFalse(state.isLoading)
    assertFalse(state.isEmpty)
  }

  @Test
  fun failureAfterAList_keepsTheList_andRecoveryClearsTheError() {
    auth.currentUser.value = ALICE
    notifications.setPending(ALICE.uid, listOf(MILO_SEEN))
    val vm = viewModel()

    notifications.setFailure(ALICE.uid, IllegalStateException("offline"))
    assertEquals(listOf(MILO_SEEN), vm.uiState.value.items)
    assertEquals(NotificationsViewModel.LOAD_FAILED, vm.uiState.value.errorMessage)

    notifications.setPending(ALICE.uid, emptyList())
    assertNull(vm.uiState.value.errorMessage)
    assertTrue(vm.uiState.value.isEmpty)
  }

  @Test
  fun retry_observesTheListAgain() {
    auth.currentUser.value = ALICE
    notifications.setFailure(ALICE.uid, IllegalStateException("offline"))
    val vm = viewModel()
    assertEquals(NotificationsViewModel.LOAD_FAILED, vm.uiState.value.errorMessage)

    notifications.setPending(ALICE.uid, listOf(MILO_SEEN))
    vm.retry()

    assertNull(vm.uiState.value.errorMessage)
    assertEquals(listOf(MILO_SEEN), vm.uiState.value.items)
    assertEquals(listOf(ALICE.uid, ALICE.uid), notifications.observedOwners)
  }

  @Test
  fun userSwitch_neverShowsThePreviousUsersItems() {
    notifications.setPending(ALICE.uid, listOf(MILO_SEEN))
    notifications.setPending(BOB.uid, listOf(REX_SEEN))
    auth.currentUser.value = ALICE
    val vm = viewModel()
    val seen = mutableListOf<List<SightingNotification>>()
    vm.uiState.value.let { seen += it.items }

    auth.currentUser.value = BOB
    seen += vm.uiState.value.items
    // A late update for Alice must not reach Bob's screen
    notifications.setPending(ALICE.uid, listOf(MILO_SEEN, MILO_SEEN_AGAIN))

    assertEquals(listOf(listOf(MILO_SEEN), listOf(REX_SEEN)), seen)
    assertEquals(listOf(REX_SEEN), vm.uiState.value.items)
    assertEquals(listOf(ALICE.uid, BOB.uid), notifications.observedOwners)
  }

  @Test
  fun signOut_clearsTheList() {
    notifications.setPending(ALICE.uid, listOf(MILO_SEEN))
    auth.currentUser.value = ALICE
    val vm = viewModel()

    auth.currentUser.value = null

    assertTrue(vm.uiState.value.items.isEmpty())
    assertFalse(vm.uiState.value.isLoading)
  }

  @Test
  fun userSwitch_resetsAPendingNavigationAndTheError() {
    auth.currentUser.value = ALICE
    notifications.setFailure(ALICE.uid, IllegalStateException("offline"))
    val vm = viewModel()
    vm.open(MILO_SEEN)

    auth.currentUser.value = BOB

    assertNull(vm.uiState.value.destinationAlertId)
    assertNull(vm.uiState.value.errorMessage)
  }

  @Test
  fun open_setsTheAlertToOpen_andOnNavigatedClearsIt() {
    auth.currentUser.value = ALICE
    notifications.setPending(ALICE.uid, listOf(MILO_SEEN))
    val vm = viewModel()

    vm.open(MILO_SEEN)
    assertEquals(MILO_SEEN.alertId, vm.uiState.value.destinationAlertId)
    assertEquals(listOf(MILO_SEEN), vm.uiState.value.items)

    vm.onNavigated()
    assertNull(vm.uiState.value.destinationAlertId)
  }

  @Test
  fun factory_buildsTheViewModel() {
    // The Factory uses the real Firebase singletons, which don't exist in a unit test
    val collection = mock<CollectionReference>()
    val firestore = mock<FirebaseFirestore> { on { collection(any()) } doReturn collection }
    val firebaseAuth = mock<FirebaseAuth>()
    mockStatic(FirebaseAuth::class.java).use { authStatic ->
      mockStatic(FirebaseFirestore::class.java).use { firestoreStatic ->
        authStatic.`when`<FirebaseAuth> { FirebaseAuth.getInstance() }.thenReturn(firebaseAuth)
        firestoreStatic
            .`when`<FirebaseFirestore> { FirebaseFirestore.getInstance() }
            .thenReturn(firestore)

        val vm =
            NotificationsViewModel.Factory.create(
                NotificationsViewModel::class.java,
                MutableCreationExtras(),
            )

        assertNull(vm.uiState.value.destinationAlertId)
      }
    }
  }

  /** [AuthRepository] whose signed-in user is set directly by the test. */
  private class SwitchableAuthRepository : AuthRepository {
    override val currentUser = MutableStateFlow<User?>(null)

    override suspend fun signInWithGoogle(idToken: String) = Result.success(ALICE)

    override suspend fun signOut(): Result<Unit> {
      currentUser.value = null
      return Result.success(Unit)
    }

    override suspend fun deleteAccount() = signOut()
  }

  private companion object {
    val ALICE = User(uid = "alice", email = "alice@example.com")
    val BOB = User(uid = "bob", email = "bob@example.com")

    fun seen(sightingId: String, alertId: String, petName: String, seenAt: Long) =
        SightingNotification(
            sightingId = sightingId,
            alertId = alertId,
            petName = petName,
            sightingPhotoUrl = null,
            seenAtMillis = seenAt,
        )

    val MILO_SEEN = seen("s-milo", "alert-milo", "Milo", 200L)
    val MILO_SEEN_AGAIN = seen("s-milo-2", "alert-milo", "Milo", 300L)
    val REX_SEEN = seen("s-rex", "alert-rex", "Rex", 100L)
  }
}
