// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.notifications

import androidx.lifecycle.viewmodel.MutableCreationExtras
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.User
import com.android.spotted.model.fakes.FakeNotificationRepository
import com.android.spotted.model.fakes.FakeNotifier
import com.android.spotted.model.notification.SightingNotification
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class SightingNotifierViewModelTest {

  private val auth = SwitchableAuthRepository()
  private val repository = FakeNotificationRepository()
  private val notifier = FakeNotifier()

  @Before
  fun setUp() {
    // Unconfined: every emission of the fakes reaches the notifier before the next line runs
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel() = SightingNotifierViewModel(auth, repository, notifier)

  @Test
  fun followsTheSignedInState_andWatchesNobodyWhenSignedOut() {
    val state = viewModel().uiState

    assertFalse(state.value.isSignedIn)
    assertTrue(repository.observedOwners.isEmpty())

    auth.currentUser.value = ALICE
    assertTrue(state.value.isSignedIn)
    assertEquals(listOf(ALICE.uid), repository.observedOwners)

    auth.currentUser.value = null
    assertFalse(state.value.isSignedIn)
  }

  @Test
  fun notifiesOnlySightingsArrivingAfterTheFirstList_eachOnce() {
    repository.setPending(ALICE.uid, listOf(FIRST))
    auth.currentUser.value = ALICE
    viewModel()
    assertTrue("already pending at launch", notifier.sightings.isEmpty())

    repository.setPending(ALICE.uid, listOf(SECOND, FIRST))
    assertEquals(listOf(SECOND), notifier.sightings)

    // A sighting reviewed by the owner leaves the list: nothing new to say, and no repeat
    repository.setPending(ALICE.uid, listOf(SECOND))
    repository.setPending(ALICE.uid, listOf(SECOND, FIRST))
    assertEquals(listOf(SECOND), notifier.sightings)
  }

  @Test
  fun anEmptyFirstList_makesTheFirstSightingNews() {
    auth.currentUser.value = ALICE
    viewModel()

    repository.setPending(ALICE.uid, listOf(FIRST, SECOND))

    assertEquals(listOf(FIRST, SECOND), notifier.sightings)
  }

  @Test
  fun aFailureNotifiesNothing_andForgetsNothing() {
    repository.setPending(ALICE.uid, listOf(FIRST))
    auth.currentUser.value = ALICE
    viewModel()

    repository.setFailure(ALICE.uid, RuntimeException("offline"))
    assertTrue(notifier.sightings.isEmpty())

    repository.setPending(ALICE.uid, listOf(SECOND, FIRST))
    assertEquals(listOf(SECOND), notifier.sightings)
  }

  @Test
  fun whenTheFirstReadFails_theNextListIsTheStartingPoint() {
    repository.setFailure(ALICE.uid, RuntimeException("offline"))
    auth.currentUser.value = ALICE
    viewModel()

    repository.setPending(ALICE.uid, listOf(FIRST))
    assertTrue(notifier.sightings.isEmpty())

    repository.setPending(ALICE.uid, listOf(SECOND, FIRST))
    assertEquals(listOf(SECOND), notifier.sightings)
  }

  @Test
  fun switchingUser_stopsWatchingThePreviousOne_andStartsOver() {
    repository.setPending(BOB.uid, listOf(BOBS))
    auth.currentUser.value = ALICE
    viewModel()

    auth.currentUser.value = BOB
    assertTrue("Bob's sighting was already pending", notifier.sightings.isEmpty())

    repository.setPending(ALICE.uid, listOf(FIRST))
    assertTrue("Alice is signed out", notifier.sightings.isEmpty())

    repository.setPending(BOB.uid, listOf(SECOND, BOBS))
    assertEquals(listOf(SECOND), notifier.sightings)
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
            SightingNotifierViewModel.Factory(notifier)
                .create(SightingNotifierViewModel::class.java, MutableCreationExtras())

        // Not the signed-in state: the auth singleton is shared with the other test classes
        assertNotNull(vm.uiState.value)
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

    fun seen(sightingId: String) =
        SightingNotification(
            sightingId = sightingId,
            alertId = "alert",
            petName = "Milo",
            sightingPhotoUrl = null,
            seenAtMillis = 100L,
        )

    val FIRST = seen("first")
    val SECOND = seen("second")
    val BOBS = seen("bobs")
  }
}
