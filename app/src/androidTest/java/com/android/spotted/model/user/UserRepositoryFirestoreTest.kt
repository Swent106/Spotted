package com.android.spotted.model.user

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.data.user.UserRepositoryFirestore
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserRepositoryFirestoreTest {
  private lateinit var auth: FirebaseAuth
  private lateinit var firestore: FirebaseFirestore
  private lateinit var repository: UserRepositoryFirestore

  @Before
  fun setUp() = runTest {
    withContext(Dispatchers.IO) {
      auth = FirebaseAuth.getInstance()
      firestore = FirebaseFirestore.getInstance()
      repository = UserRepositoryFirestore(firestore)
      Tasks.await(auth.signInAnonymously())
    }
  }

  @After
  fun cleanUp() {
    auth.signOut()
  }

  @Test
  fun saveUser_andGetUserFromNewRepository_persistsAllProfileFields() = runTest {
    val profile =
        UserProfile(
            uid = currentUserId(),
            name = "Jamie",
            phone = "+41 79 123 45 67",
            email = "jamie@example.com",
            homeArea = "Lausanne",
        )

    assertTrue(repository.saveUser(profile).isSuccess)

    val reopenedRepository = UserRepositoryFirestore(firestore)
    assertEquals(profile, reopenedRepository.getUser(profile.uid).getOrThrow())
  }

  @Test
  fun saveUser_withDefaultOptionalFields_persistsEmptyValues() = runTest {
    val profile =
        UserProfile(
            uid = currentUserId(),
            name = "Jamie",
            email = "jamie@example.com",
        )

    assertTrue(repository.saveUser(profile).isSuccess)
    assertEquals(profile, repository.getUser(profile.uid).getOrThrow())
  }

  @Test
  fun getUser_whenProfileDoesNotExist_returnsFailure() = runTest {
    val result = repository.getUser(currentUserId())

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is NoSuchElementException)
  }

  @Test
  fun getUser_andSaveUser_forAnotherUid_areDenied() = runTest {
    val otherUid = "another-user"
    val profile =
        UserProfile(
            uid = otherUid,
            name = "Alex",
            email = "alex@example.com",
        )

    assertTrue(repository.saveUser(profile).isFailure)
    assertTrue(repository.getUser(otherUid).isFailure)
  }

  @Test
  fun observeUser_emitsTheProfile_thenEachChange() = runTest {
    val profile = UserProfile(uid = currentUserId(), name = "Jamie", email = "jamie@example.com")
    repository.saveUser(profile).getOrThrow()

    // Real time: the listener answers from the emulator, which virtual time doesn't wait for
    withContext(Dispatchers.IO) {
      withTimeout(TIMEOUT_MILLIS) {
        repository.observeUser(profile.uid).first { it.getOrNull() == profile }
      }
      val moved = profile.copy(alertRadiusKm = 10)
      withTimeout(TIMEOUT_MILLIS) {
        repository.observeUser(profile.uid).first {
          // The first emission can still be the old profile; the change follows it
          if (it.getOrNull() == profile) repository.saveUser(moved).getOrThrow()
          it.getOrNull() == moved
        }
      }
    }
  }

  @Test
  fun observeUser_forAnotherUid_emitsAFailure() = runTest {
    val result =
        withContext(Dispatchers.IO) {
          withTimeout(TIMEOUT_MILLIS) { repository.observeUser("another-user").first() }
        }

    assertTrue(result.isFailure)
  }

  private fun currentUserId(): String =
      requireNotNull(auth.currentUser) { "Test user was not authenticated" }.uid

  private companion object {
    const val TIMEOUT_MILLIS = 10_000L
  }
}
