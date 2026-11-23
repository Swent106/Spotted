// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.user

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.data.user.UserRepositoryFirestore
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
            username = "jamie",
        )

    assertTrue(repository.saveUser(profile).isSuccess)

    val reopenedRepository = UserRepositoryFirestore(firestore)
    assertEquals(profile, reopenedRepository.getUser(profile.uid).getOrThrow())
  }

  @Test
  fun saveUser_withDefaultOptionalFields_persistsEmptyValues_butNeedsAUsername() = runTest {
    val profile =
        UserProfile(
            uid = currentUserId(),
            name = "Jamie",
            email = "jamie@example.com",
            username = "jamie",
        )

    assertTrue(repository.saveUser(profile).isSuccess)
    assertEquals(profile, repository.getUser(profile.uid).getOrThrow())
    assertTrue(
        "the rules refuse a profile without a username",
        repository.saveUser(profile.copy(username = "")).isFailure,
    )
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
    val profile =
        UserProfile(
            uid = currentUserId(),
            name = "Jamie",
            username = "jamie",
            email = "jamie@example.com",
        )
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

  @Test
  fun saveUser_firstSave_setsCreatedAtToTheServerTime() = runTest {
    val before = System.currentTimeMillis()

    repository.saveUser(jamie()).getOrThrow()

    val createdAt = checkNotNull(storedCreatedAt()) { "createdAt was not stored" }
    // The emulator's clock is this machine's: a minute of margin either way
    assertTrue(createdAt.toDate().time in (before - MINUTE_MILLIS)..(before + MINUTE_MILLIS))
  }

  @Test
  fun saveUser_again_keepsCreatedAt() = runTest {
    repository.saveUser(jamie()).getOrThrow()
    val createdAt = storedCreatedAt()

    repository.saveUser(jamie().copy(name = "Alex", alertRadiusKm = 10)).getOrThrow()

    assertNotNull(createdAt)
    assertEquals(createdAt, storedCreatedAt())
    assertEquals("Alex", repository.getUser(currentUserId()).getOrThrow().name)
  }

  @Test
  fun createProfileWithoutCreatedAt_isRefused() = runTest {
    val result = runCatching {
      withContext(Dispatchers.IO) { Tasks.await(profileDocument().set(jamieFields())) }
    }

    assertPermissionDenied(result.exceptionOrNull())
    assertTrue(repository.getUser(currentUserId()).exceptionOrNull() is NoSuchElementException)
  }

  @Test
  fun updateRemovingCreatedAt_isRefused() = runTest {
    repository.saveUser(jamie()).getOrThrow()
    val createdAt = storedCreatedAt()

    val result = runCatching {
      withContext(Dispatchers.IO) {
        Tasks.await(profileDocument().update("createdAt", FieldValue.delete()))
      }
    }

    assertPermissionDenied(result.exceptionOrNull())
    assertNotNull(createdAt)
    assertEquals(createdAt, storedCreatedAt())
  }

  private fun jamie() =
      UserProfile(
          uid = currentUserId(),
          name = "Jamie",
          username = "jamie",
          email = "jamie@example.com",
      )

  /** A valid profile of the signed-in user, as stored, but without createdAt. */
  private fun jamieFields() =
      mapOf(
          "uid" to currentUserId(),
          "name" to "Jamie",
          "username" to "jamie",
          "phone" to "",
          "email" to "jamie@example.com",
          "homeArea" to "",
      )

  private fun profileDocument() = firestore.collection("users").document(currentUserId())

  /** The createdAt stored in the signed-in user's profile document, read from the server. */
  private suspend fun storedCreatedAt(): Timestamp? =
      withContext(Dispatchers.IO) {
        Tasks.await(profileDocument().get(Source.SERVER)).getTimestamp("createdAt")
      }

  private fun assertPermissionDenied(error: Throwable?) {
    val cause = if (error is ExecutionException) error.cause else error
    assertTrue(
        "expected PERMISSION_DENIED, was $error",
        (cause as? FirebaseFirestoreException)?.code ==
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
    )
  }

  private fun currentUserId(): String =
      requireNotNull(auth.currentUser) { "Test user was not authenticated" }.uid

  private companion object {
    const val TIMEOUT_MILLIS = 10_000L
    const val MINUTE_MILLIS = 60_000L
  }
}
