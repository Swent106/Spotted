// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.thanks

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.thanks.AlreadyThankedException
import com.android.spotted.model.thanks.Thanks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** [ThanksRepositoryFirestore] against the Firestore emulator, with its security rules. */
@RunWith(AndroidJUnit4::class)
class ThanksRepositoryFirestoreTest {
  private lateinit var firestore: FirebaseFirestore
  private lateinit var auth: FirebaseAuth
  private lateinit var repository: ThanksRepositoryFirestore
  private lateinit var me: String
  private val helper = "helper-${UUID.randomUUID()}"

  // Paths written past the rules, deleted the same way (no client may delete a thanks)
  private val seededPaths = mutableListOf<String>()

  @Before
  fun setUp() = runBlocking {
    firestore = FirebaseFirestore.getInstance()
    auth = FirebaseAuth.getInstance()
    repository = ThanksRepositoryFirestore(firestore)
    me = auth.signInAnonymously().await().user!!.uid
  }

  @After
  fun tearDown() = runBlocking {
    seededPaths.forEach { runCatching { emulatorRequest("DELETE", it) } }
    auth.signOut()
  }

  /**
   * Seeds a closed alert of mine and a sighting [helperId] sent on it; returns their thanks. Pass
   * [alertId] to control the document id, and so the order Firestore returns the thanks in.
   */
  private suspend fun closedAlertWithHelper(
      thankedAtMillis: Long = 1_000L,
      alertId: String = "alert-${UUID.randomUUID()}",
      helperId: String = helper,
  ): Thanks {
    val sightingId = "sighting-${UUID.randomUUID()}"
    seed("alerts/$alertId", mapOf("ownerId" to me, "status" to "CLOSED", "petName" to "Milo"))
    seed("sightings/$sightingId", mapOf("reporterId" to helperId, "alertId" to alertId))
    val thanks =
        Thanks(
            alertId = alertId,
            ownerId = me,
            helperId = helperId,
            sightingId = sightingId,
            petName = "Milo",
            thankedAtMillis = thankedAtMillis,
        )
    seededPaths += "thanks/${thanks.id}"
    return thanks
  }

  @Test
  fun thank_isStoredAndCounted() = runBlocking {
    val thanks = closedAlertWithHelper()

    val result = repository.thank(thanks)

    assertTrue("thank should succeed: ${result.exceptionOrNull()}", result.isSuccess)
    assertEquals(1, repository.getThanksCount(helper).getOrThrow())
  }

  @Test
  fun getThanksCount_neverThanked_returnsZero() = runBlocking {
    assertEquals(0, repository.getThanksCount(helper).getOrThrow())
  }

  @Test
  fun thank_twice_failsWithAlreadyThankedAndCountsOnce() = runBlocking {
    val thanks = closedAlertWithHelper()
    repository.thank(thanks).getOrThrow()

    val result = repository.thank(thanks.copy(thankedAtMillis = 2_000L))

    assertTrue(
        "Expected AlreadyThankedException, got ${result.exceptionOrNull()}",
        result.exceptionOrNull() is AlreadyThankedException,
    )
    assertEquals(1, repository.getThanksCount(helper).getOrThrow())
  }

  @Test
  fun thank_twoAtOnce_storesOne() = runBlocking {
    val thanks = closedAlertWithHelper()
    // Both calls wait for the same signal, so they start together and their transactions overlap
    val start = CompletableDeferred<Unit>()
    val calls =
        List(2) {
          async(Dispatchers.IO) {
            start.await()
            repository.thank(thanks)
          }
        }

    start.complete(Unit)
    val results = withTimeout(TIMEOUT_MILLIS) { calls.awaitAll() }

    assertEquals("Exactly one should succeed: $results", 1, results.count { it.isSuccess })
    val error = results.single { it.isFailure }.exceptionOrNull()
    assertTrue("Expected AlreadyThankedException, got $error", error is AlreadyThankedException)
    assertEquals(1, repository.getThanksCount(helper).getOrThrow())
  }

  @Test
  fun observeThanksReceived_listsOnlyTheHelpersThanks_newestFirst() = runBlocking {
    // The older thanks has the id that sorts first: Firestore returns [older, newer], so only the
    // repository's sort can give [newer, older]
    val older = closedAlertWithHelper(thankedAtMillis = 1_000L, alertId = "alert-a-$RUN")
    val newer = closedAlertWithHelper(thankedAtMillis = 2_000L, alertId = "alert-b-$RUN")
    val otherHelpers =
        closedAlertWithHelper(thankedAtMillis = 3_000L, helperId = "other-${UUID.randomUUID()}")
    repository.thank(older).getOrThrow()
    repository.thank(newer).getOrThrow()
    repository.thank(otherHelpers).getOrThrow()

    val received = firstThanksList { it.size == 2 }

    assertEquals(listOf(newer, older), received)
  }

  @Test
  fun observeThanksReceived_newThanks_arrivesLive() = runBlocking {
    val updates = Channel<Result<List<Thanks>>>(Channel.UNLIMITED)
    val collector =
        launch(Dispatchers.IO) {
          repository.observeThanksReceived(helper).collect { updates.send(it) }
        }
    try {
      assertEquals(
          emptyList<Thanks>(),
          withTimeout(TIMEOUT_MILLIS) { updates.receive() }.getOrThrow(),
      )
      val thanks = closedAlertWithHelper()

      repository.thank(thanks).getOrThrow()

      // Subscribed before the thanks was written: it must come as an update
      val received =
          withTimeout(TIMEOUT_MILLIS) {
            var list = updates.receive().getOrThrow()
            while (list.isEmpty()) list = updates.receive().getOrThrow()
            list
          }
      assertEquals(listOf(thanks), received)
    } finally {
      collector.cancel()
    }
  }

  @Test
  fun observeThanksReceived_signedOut_emitsPermissionDenied() = runBlocking {
    auth.signOut()

    val result = withTimeout(TIMEOUT_MILLIS) { repository.observeThanksReceived(helper).first() }

    assertPermissionDenied(result)
  }

  @Test
  fun observeThanksReceived_malformedDocuments_areSkipped() = runBlocking {
    // Written past the rules: one without a pet name, one with a time that is not a number
    seed(
        "thanks/broken-1-$RUN",
        mapOf("alertId" to "a", "ownerId" to me, "helperId" to helper, "sightingId" to "s"),
        longs = mapOf("thankedAtMillis" to 1_000L),
    )
    seed(
        "thanks/broken-2-$RUN",
        mapOf(
            "alertId" to "a",
            "ownerId" to me,
            "helperId" to helper,
            "sightingId" to "s",
            "petName" to "Milo",
            "thankedAtMillis" to "yesterday",
        ),
    )
    // Seeded on the server too, not written through this client: the first non-empty list then
    // comes from the server, with the broken documents, not from the local cache without them
    val valid =
        Thanks(
            alertId = "alert-$RUN",
            ownerId = me,
            helperId = helper,
            sightingId = "sighting-$RUN",
            petName = "Milo",
            thankedAtMillis = 1_000L,
        )
    seed(
        "thanks/${valid.id}",
        mapOf(
            "alertId" to valid.alertId,
            "ownerId" to valid.ownerId,
            "helperId" to valid.helperId,
            "sightingId" to valid.sightingId,
            "petName" to valid.petName,
        ),
        longs = mapOf("thankedAtMillis" to valid.thankedAtMillis),
    )

    val received = firstThanksList { it.isNotEmpty() }

    assertEquals(listOf(valid), received)
  }

  @Test
  fun thank_withoutTheHelpersSighting_isRefusedByTheRules() = runBlocking {
    val thanks = closedAlertWithHelper()

    val result = repository.thank(thanks.copy(sightingId = "missing-${UUID.randomUUID()}"))

    assertPermissionDenied(result)
    assertEquals(0, repository.getThanksCount(helper).getOrThrow())
  }

  @Test
  fun thank_yourself_fails() = runBlocking {
    val thanks = closedAlertWithHelper()

    val result = repository.thank(thanks.copy(helperId = me))

    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
  }

  @Test
  fun getThanksCount_signedOut_isRefusedByTheRules() = runBlocking {
    auth.signOut()

    assertPermissionDenied(repository.getThanksCount(helper))
  }

  /** The first list of [helper]'s thanks matching [predicate]; fails instead of hanging. */
  private suspend fun firstThanksList(predicate: (List<Thanks>) -> Boolean): List<Thanks> =
      withTimeout(TIMEOUT_MILLIS) {
        repository
            .observeThanksReceived(helper)
            .first { it.isFailure || predicate(it.getOrThrow()) }
            .getOrThrow()
      }

  /** [result] failed because the security rules refused it, not for any other reason. */
  private fun assertPermissionDenied(result: Result<*>) {
    val error = result.exceptionOrNull()
    assertTrue(
        "Expected PERMISSION_DENIED, got $error",
        (error as? FirebaseFirestoreException)?.code ==
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
    )
  }

  private suspend fun seed(
      path: String,
      strings: Map<String, String>,
      longs: Map<String, Long> = emptyMap(),
  ) {
    val fields =
        strings.mapValues { mapOf("stringValue" to it.value) } +
            longs.mapValues { mapOf("integerValue" to it.value.toString()) }
    val collection = path.substringBeforeLast('/')
    val id = path.substringAfterLast('/')
    val response =
        emulatorRequest("POST", "$collection?documentId=$id", JSONObject(mapOf("fields" to fields)))
    assertEquals(HttpURLConnection.HTTP_OK, response)
    seededPaths += path
  }

  /** Calls the emulator's REST API as its owner, which skips the security rules. */
  private suspend fun emulatorRequest(method: String, path: String, body: JSONObject? = null): Int {
    val url =
        URL(
            "http://${firestore.firestoreSettings.host}/v1/projects/${firestore.app.options.projectId}" +
                "/databases/(default)/documents/$path"
        )
    return withContext(Dispatchers.IO) {
      val connection = url.openConnection() as HttpURLConnection
      try {
        connection.requestMethod = method
        connection.setRequestProperty("Authorization", "Bearer owner")
        if (body != null) {
          connection.doOutput = true
          connection.setRequestProperty("Content-Type", "application/json")
          connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
        }
        connection.responseCode
      } finally {
        connection.disconnect()
      }
    }
  }

  private companion object {
    /** Long enough for the emulator, short enough to fail fast instead of hanging. */
    const val TIMEOUT_MILLIS = 15_000L

    /** Unique per run, so ids chosen for their order don't clash with an earlier run. */
    val RUN = UUID.randomUUID().toString()
  }
}
