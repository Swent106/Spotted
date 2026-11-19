// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.moderation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** [BlockRepositoryFirestore] against the Firestore emulator, with its security rules. */
@RunWith(AndroidJUnit4::class)
class BlockRepositoryFirestoreTest {
  private lateinit var auth: FirebaseAuth
  private lateinit var repository: BlockRepositoryFirestore
  private lateinit var me: String
  private val spammer = "spammer-${UUID.randomUUID()}"
  private val troll = "troll-${UUID.randomUUID()}"

  @Before
  fun setUp() = runTest {
    withContext(Dispatchers.IO) {
      auth = FirebaseAuth.getInstance()
      repository = BlockRepositoryFirestore(FirebaseFirestore.getInstance())
      me = Tasks.await(auth.signInAnonymously()).user!!.uid
    }
  }

  @After
  fun cleanUp() = runTest {
    repository.unblock(me, spammer)
    repository.unblock(me, troll)
    withContext(Dispatchers.IO) { auth.signOut() }
  }

  private suspend fun blocked() = repository.observeBlocked(me).first().getOrThrow()

  @Test
  fun block_addsToMyList_andUnblockRemoves() = runTest {
    assertTrue(repository.block(me, spammer).isSuccess)
    assertTrue(repository.block(me, troll).isSuccess)
    assertEquals(setOf(spammer, troll), blocked())

    assertTrue(repository.unblock(me, spammer).isSuccess)
    assertEquals(setOf(troll), blocked())
  }

  @Test
  fun blockingTwice_andUnblockingSomeoneNotBlocked_succeed() = runTest {
    assertTrue(repository.block(me, spammer).isSuccess)
    assertTrue(repository.block(me, spammer).isSuccess)
    assertTrue(repository.unblock(me, troll).isSuccess)

    assertEquals(setOf(spammer), blocked())
  }

  @Test fun emptyList_isEmpty() = runTest { assertEquals(emptySet<String>(), blocked()) }

  @Test
  fun blockingMyself_fails() = runTest {
    val result = repository.block(me, me)

    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    assertEquals(emptySet<String>(), blocked())
  }

  @Test
  fun someoneElsesList_canBeNeitherReadNorChanged() = runTest {
    val other = "other-${UUID.randomUUID()}"

    assertTrue(repository.block(other, spammer).isFailure)
    assertTrue(repository.unblock(other, spammer).isFailure)
    assertTrue(repository.observeBlocked(other).first().isFailure)
  }
}
