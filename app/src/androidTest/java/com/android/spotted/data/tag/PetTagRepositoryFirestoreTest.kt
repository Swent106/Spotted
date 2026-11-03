// Portions of this code were generated with the help of Copilot.
package com.android.spotted.data.tag

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.model.tag.PetTag
import com.android.spotted.model.tag.TagAlreadyLinkedException
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PetTagRepositoryFirestoreTest {
  private lateinit var auth: FirebaseAuth
  private lateinit var firestore: FirebaseFirestore
  private lateinit var repository: PetTagRepositoryFirestore
  private val insertedPetIds = mutableListOf<String>()
  private val insertedTagIds = mutableListOf<String>()
  private var currentTimeMillis = 100L

  @Before
  fun setUp() = runTest {
    withContext(Dispatchers.IO) {
      auth = FirebaseAuth.getInstance()
      firestore = FirebaseFirestore.getInstance()
      repository = PetTagRepositoryFirestore(firestore, auth) { currentTimeMillis }
      Tasks.await(auth.signInAnonymously())
    }
  }

  @After
  fun cleanUp() = runTest {
    withContext(Dispatchers.IO) {
      insertedTagIds.forEach { id -> emulatorRequest("DELETE", "petTags/$id") }
      insertedPetIds.forEach { id -> emulatorRequest("DELETE", "pets/$id") }
      auth.signOut()
    }
  }

  @Test
  fun linkMoveReadUnlinkAndUnknownTag() = runTest {
    val petId = addPet()
    val secondPetId = addPet()
    val tagId = "A1B2C3D4"
    insertedTagIds += tagId

    repository.linkTag(tagId, petId).getOrThrow()
    assertEquals(tagId, repository.getTagForPet(petId).getOrThrow()?.tagId)
    currentTimeMillis = 200L

    repository.linkTag(tagId, secondPetId).getOrThrow()
    assertEquals(
        PetTag(tagId, secondPetId, currentUserId(), currentTimeMillis),
        repository.getTag(tagId).getOrThrow(),
    )
    assertNull(repository.getTagForPet(petId).getOrThrow())
    repository.unlinkTag(tagId).getOrThrow()
    assertNull(repository.getTag(tagId).getOrThrow())
  }

  @Test
  fun linkTag_failsWhenTagIsOwnedByAnotherUser() = runTest {
    val petId = addPet()
    val tagId = "A1B2C3D4"
    insertedTagIds += tagId
    seedTagBypassingRules(PetTag(tagId, "another-pet", "another-owner", 100L))

    val result = repository.linkTag(tagId, petId)

    assertTrue(result.exceptionOrNull() is TagAlreadyLinkedException)
    assertEquals("another-owner", repository.getTag(tagId).getOrThrow()?.ownerId)
  }

  private suspend fun addPet(): String {
    val id = "pet-${insertedPetIds.size + 1}"
    insertedPetIds += id
    Tasks.await(firestore.collection("pets").document(id).set(mapOf("ownerId" to currentUserId())))
    return id
  }

  private fun currentUserId(): String =
      requireNotNull(auth.currentUser) { "Test user was not authenticated" }.uid

  private suspend fun seedTagBypassingRules(tag: PetTag) {
    val fields =
        mapOf(
            "tagId" to mapOf("stringValue" to tag.tagId),
            "petId" to mapOf("stringValue" to tag.petId),
            "ownerId" to mapOf("stringValue" to tag.ownerId),
            "linkedAtMillis" to mapOf("integerValue" to tag.linkedAtMillis.toString()),
        )
    val response =
        emulatorRequest(
            "POST",
            "petTags?documentId=${tag.tagId}",
            JSONObject(mapOf("fields" to fields)),
        )
    assertEquals(HttpURLConnection.HTTP_OK, response)
  }

  private suspend fun emulatorRequest(
      method: String,
      path: String,
      body: JSONObject? = null,
  ): Int {
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
