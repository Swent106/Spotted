// Portions of this code were generated with the help of Copilot.
// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.tag

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.spotted.data.user.UserRepositoryFirestore
import com.android.spotted.model.tag.PetTag
import com.android.spotted.model.tag.TagAlreadyLinkedException
import com.android.spotted.model.user.UserProfile
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
  private lateinit var userRepository: UserRepositoryFirestore
  private lateinit var ownerId: String
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
      ownerId = currentUserId()
      userRepository = UserRepositoryFirestore(firestore)
      userRepository
          .saveUser(
              UserProfile(
                  uid = ownerId,
                  name = "Jamie",
                  phone = "+41 79 123 45 67",
                  email = "jamie@example.com",
                  username = "jamie",
              )
          )
          .getOrThrow()
    }
  }

  @After
  fun cleanUp() = runTest {
    withContext(Dispatchers.IO) {
      insertedTagIds.forEach { id -> emulatorRequest("DELETE", "petTags/$id") }
      insertedPetIds.forEach { id -> emulatorRequest("DELETE", "pets/$id") }
      userRepository.deleteUser(ownerId)
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
        PetTag(
            tagId,
            secondPetId,
            currentUserId(),
            currentTimeMillis,
            showPhone = true,
            showEmail = true,
            contactPhone = "+41 79 123 45 67",
            contactEmail = "jamie@example.com",
        ),
        repository.getTag(tagId).getOrThrow(),
    )
    assertNull(repository.getTagForPet(petId).getOrThrow())
    Tasks.await(firestore.collection("pets").document(secondPetId).delete())
    insertedPetIds.remove(secondPetId)
    repository.unlinkTag(tagId).getOrThrow()
    assertNull(repository.getTag(tagId).getOrThrow())
  }

  @Test
  fun linkTag_failsWhenTagIsOwnedByAnotherUser() = runTest {
    val petId = addPet()
    val tagId = "A1B2C3D4"
    insertedTagIds += tagId
    seedTagBypassingRules(PetTag(tagId, "another-pet", "another-owner", 100L))

    assertTrue(repository.unlinkTag(tagId).exceptionOrNull() is SecurityException)
    val result = repository.linkTag(tagId, petId)

    assertTrue(result.exceptionOrNull() is TagAlreadyLinkedException)
    assertEquals("another-owner", repository.getTag(tagId).getOrThrow()?.ownerId)
  }

  @Test
  fun linkTag_failsWhenPetDoesNotExist() = runTest {
    val result = repository.linkTag("A1B2C3D4", "missing-pet")

    assertTrue(result.isFailure)
    assertNull(repository.getTag("A1B2C3D4").getOrThrow())
  }

  @Test
  fun linkTag_failsWhenSignedOut() = runTest {
    auth.signOut()

    assertTrue(repository.linkTag("A1B2C3D4", "pet-1").exceptionOrNull() is IllegalStateException)
    assertTrue(repository.unlinkTag("A1B2C3D4").exceptionOrNull() is IllegalStateException)
    assertTrue(repository.getTag("A1B2C3D4").exceptionOrNull() is IllegalStateException)
    assertTrue(repository.getTagForPet("pet-1").exceptionOrNull() is IllegalStateException)
  }

  @Test
  fun getNewId_isUniqueAndAcceptedAsATagId() = runTest {
    val first = repository.getNewId()
    val second = repository.getNewId()

    assertTrue(first.matches(Regex("[0-9A-F]{32}")))
    assertTrue(first != second)
    // The security rules accept it: a tag can be linked with it.
    val petId = addPet()
    insertedTagIds += first
    repository.linkTag(first, petId).getOrThrow()
    assertEquals(petId, repository.getTag(first).getOrThrow()?.petId)
  }

  @Test
  fun unlinkTag_succeedsWhenTagDoesNotExist() = runTest {
    repository.unlinkTag("A1B2C3D4").getOrThrow()

    assertNull(repository.getTag("A1B2C3D4").getOrThrow())
  }

  @Test
  fun getTagAndGetTagForPet_failForMalformedStoredTag() = runTest {
    val tagId = "A1B2C3D4"
    insertedTagIds += tagId
    seedMalformedTagBypassingRules(tagId, "pet-1", currentUserId())

    assertTrue(repository.getTag(tagId).isFailure)
    assertTrue(repository.getTagForPet("pet-1").isFailure)
  }

  @Test
  fun setVisibleContacts_hidesPhoneAndStoresNullDespiteSuppliedValue() = runTest {
    val tagId = linkTestTag()

    repository
        .setVisibleContacts(
            tagId,
            showPhone = false,
            showEmail = true,
            phone = "+41 79 123 45 67",
            email = "jamie@example.com",
        )
        .getOrThrow()
    var tag = requireNotNull(repository.getTag(tagId).getOrThrow())
    assertTrue(!tag.showPhone)
    assertNull(tag.contactPhone)
    assertTrue(tag.showEmail)
    assertEquals("jamie@example.com", tag.contactEmail)
    val phoneSnapshot = Tasks.await(firestore.collection("petTags").document(tagId).get())
    assertTrue(phoneSnapshot.contains("contactPhone"))
    assertNull(phoneSnapshot.getString("contactPhone"))
  }

  @Test
  fun setVisibleContacts_hidesEmailAndStoresNullDespiteSuppliedValue() = runTest {
    val tagId = linkTestTag()
    repository
        .setVisibleContacts(
            tagId,
            showPhone = true,
            showEmail = false,
            phone = "+41 79 123 45 67",
            email = "jamie@example.com",
        )
        .getOrThrow()
    val tag = requireNotNull(repository.getTag(tagId).getOrThrow())
    assertTrue(tag.showPhone)
    assertEquals("+41 79 123 45 67", tag.contactPhone)
    assertTrue(!tag.showEmail)
    assertNull(tag.contactEmail)
    val emailSnapshot = Tasks.await(firestore.collection("petTags").document(tagId).get())
    assertTrue(emailSnapshot.contains("contactEmail"))
    assertNull(emailSnapshot.getString("contactEmail"))
  }

  @Test
  fun setVisibleContacts_refusesHidingBothWithoutChangingDocument() = runTest {
    val tagId = linkTestTag()
    val before = requireNotNull(repository.getTag(tagId).getOrThrow())
    val result =
        repository.setVisibleContacts(
            tagId,
            showPhone = false,
            showEmail = false,
            phone = "+41 79 123 45 67",
            email = "jamie@example.com",
        )
    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    assertEquals(before, repository.getTag(tagId).getOrThrow())
    val snapshot = Tasks.await(firestore.collection("petTags").document(tagId).get())
    assertEquals(before.contactPhone, snapshot.getString("contactPhone"))
    assertEquals(before.contactEmail, snapshot.getString("contactEmail"))
  }

  private suspend fun linkTestTag(): String {
    val petId = addPet()
    val tagId = "A1B2C3D4"
    insertedTagIds += tagId
    repository.linkTag(tagId, petId).getOrThrow()
    return tagId
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
            "showPhone" to mapOf("booleanValue" to tag.showPhone),
            "showEmail" to mapOf("booleanValue" to tag.showEmail),
            "contactPhone" to contactField(tag.contactPhone),
            "contactEmail" to contactField(tag.contactEmail),
        )
    val response =
        emulatorRequest(
            "POST",
            "petTags?documentId=${tag.tagId}",
            JSONObject(mapOf("fields" to fields)),
        )
    assertEquals(HttpURLConnection.HTTP_OK, response)
  }

  private fun contactField(value: String?): Map<String, Any?> =
      if (value == null) mapOf("nullValue" to null) else mapOf("stringValue" to value)

  private suspend fun seedMalformedTagBypassingRules(
      tagId: String,
      petId: String,
      ownerId: String,
  ) {
    val fields =
        mapOf(
            "tagId" to mapOf("stringValue" to tagId),
            "petId" to mapOf("stringValue" to petId),
            "ownerId" to mapOf("stringValue" to ownerId),
        )
    val response =
        emulatorRequest(
            "POST",
            "petTags?documentId=$tagId",
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
