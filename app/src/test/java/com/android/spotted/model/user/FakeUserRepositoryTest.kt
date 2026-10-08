package com.android.spotted.model.user

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeUserRepositoryTest {
  private val repository = FakeUserRepository()

  @Test
  fun saveUser_andGetUser_returnsSavedUser() = runTest {
    val user = user(uid = "user-1")

    assertTrue(repository.saveUser(user).isSuccess)
    assertEquals(user, repository.getUser(user.uid).getOrThrow())
  }

  @Test
  fun saveUser_storesUsersIndependently() = runTest {
    val firstUser = user(uid = "user-1")
    val secondUser = user(uid = "user-2", name = "Alex")

    repository.saveUser(firstUser)
    repository.saveUser(secondUser)

    assertEquals(firstUser, repository.getUser(firstUser.uid).getOrThrow())
    assertEquals(secondUser, repository.getUser(secondUser.uid).getOrThrow())
  }

  @Test
  fun saveUser_replacesExistingUserWithSameUid() = runTest {
    val originalUser = user(uid = "user-1")
    val updatedUser = user(uid = "user-1", name = "Alex")
    repository.saveUser(originalUser)

    assertTrue(repository.saveUser(updatedUser).isSuccess)

    assertEquals(updatedUser, repository.getUser(updatedUser.uid).getOrThrow())
  }

  @Test
  fun getUser_whenUserDoesNotExist_returnsFailure() = runTest {
    val result = repository.getUser("missing")

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is NoSuchElementException)
  }

  @Test
  fun getUser_returnsConfiguredFailure() = runTest {
    val expectedFailure = IllegalStateException("lookup failed")
    repository.failure = expectedFailure

    val result = repository.getUser("user-1")

    assertSame(expectedFailure, result.exceptionOrNull())
  }

  @Test
  fun saveUser_returnsConfiguredFailureWithoutSavingUser() = runTest {
    val expectedFailure = IllegalStateException("save failed")
    val user = user(uid = "user-1")
    repository.failure = expectedFailure

    val result = repository.saveUser(user)

    assertSame(expectedFailure, result.exceptionOrNull())
    repository.failure = null
    assertTrue(repository.getUser(user.uid).isFailure)
  }

  private fun user(uid: String, name: String = "Jamie") =
      UserProfile(
          uid = uid,
          name = name,
          phone = "+41 79 123 45 67",
          email = "jamie@example.com",
          homeArea = "Lausanne",
      )
}
