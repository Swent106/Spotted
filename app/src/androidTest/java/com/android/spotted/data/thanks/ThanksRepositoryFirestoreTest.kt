// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.thanks

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.thanks.AlreadyThankedException
import com.android.spotted.model.thanks.Thanks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
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

  /** Seeds a closed alert of mine and a sighting [helper] sent on it; returns their thanks. */
  private suspend fun closedAlertWithHelper(thankedAtMillis: Long = 1_000L): Thanks {
    val alertId = "alert-${UUID.randomUUID()}"
    val sightingId = "sighting-${UUID.randomUUID()}"
    seed(
        "alerts",
        alertId,
        mapOf("ownerId" to me, "status" to "CLOSED", "petName" to "Milo"),
    )
    seed("sightings", sightingId, mapOf("reporterId" to helper, "alertId" to alertId))
    val thanks =
        Thanks(
            alertId = alertId,
            ownerId = me,
            helperId = helper,
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
  fun neverThanked_countsZero() = runBlocking {
    assertEquals(0, repository.getThanksCount(helper).getOrThrow())
  }

  @Test
  fun thankingTwice_failsWithAlreadyThanked_andCountsOnce() = runBlocking {
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
  fun twoThanksAtOnce_storeOne() = runBlocking {
    val thanks = closedAlertWithHelper()

    val results = List(2) { async(Dispatchers.IO) { repository.thank(thanks) } }.awaitAll()

    assertEquals("Exactly one should succeed: $results", 1, results.count { it.isSuccess })
    assertTrue(results.single { it.isFailure }.exceptionOrNull() is AlreadyThankedException)
    assertEquals(1, repository.getThanksCount(helper).getOrThrow())
  }

  @Test
  fun observeThanksReceived_listsTheHelpersThanks_newestFirst() = runBlocking {
    val older = closedAlertWithHelper(thankedAtMillis = 1_000L)
    val newer = closedAlertWithHelper(thankedAtMillis = 2_000L)
    repository.thank(older).getOrThrow()
    repository.thank(newer).getOrThrow()

    val received =
        repository.observeThanksReceived(helper).first { it.getOrNull()?.size == 2 }.getOrThrow()

    assertEquals(listOf(newer, older), received)
  }

  @Test
  fun thankingWithoutTheHelpersSighting_isRefused() = runBlocking {
    val thanks = closedAlertWithHelper()

    val result = repository.thank(thanks.copy(sightingId = "missing-${UUID.randomUUID()}"))

    assertTrue("The rules should refuse it", result.isFailure)
    assertEquals(0, repository.getThanksCount(helper).getOrThrow())
  }

  @Test
  fun thankingYourself_fails() = runBlocking {
    val thanks = closedAlertWithHelper()

    val result = repository.thank(thanks.copy(helperId = me))

    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
  }

  @Test
  fun countWhenSignedOut_fails() = runBlocking {
    auth.signOut()

    assertTrue(repository.getThanksCount(helper).isFailure)
  }

  private suspend fun seed(collection: String, id: String, fields: Map<String, String>) {
    val body = JSONObject(mapOf("fields" to fields.mapValues { mapOf("stringValue" to it.value) }))
    val response = emulatorRequest("POST", "$collection?documentId=$id", body)
    assertEquals(HttpURLConnection.HTTP_OK, response)
    seededPaths += "$collection/$id"
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
}
