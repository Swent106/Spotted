// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.sighting

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.ExecutionException
import org.junit.Assert.assertEquals
import org.junit.Test

class SightingRefusalTest {

  @Test
  fun firestoreErrors_areRefusalsOnlyWhenRetryingCantHelp() {
    val refusals =
        setOf(
            Code.PERMISSION_DENIED,
            Code.INVALID_ARGUMENT,
            Code.FAILED_PRECONDITION,
            Code.NOT_FOUND,
            Code.ALREADY_EXISTS,
            Code.OUT_OF_RANGE,
        )

    // OK is not an error: Firestore refuses to create an exception for it
    Code.entries
        .filter { it != Code.OK }
        .forEach { code ->
          assertEquals(
              code.name,
              code in refusals,
              isSightingRefusal(FirebaseFirestoreException("error", code)),
          )
        }
  }

  @Test
  fun aWrappedRefusal_isStillARefusal() {
    val wrapped = ExecutionException(FirebaseFirestoreException("denied", Code.PERMISSION_DENIED))

    assertEquals(true, isSightingRefusal(wrapped))
  }

  @Test
  fun aMissingOrInvalidPhoto_isARefusal_butANetworkErrorIsNot() {
    assertEquals(true, isSightingRefusal(FileNotFoundException("photo copy gone")))
    assertEquals(true, isSightingRefusal(IllegalArgumentException("not an image")))
    assertEquals(false, isSightingRefusal(IOException("offline")))
  }
}
