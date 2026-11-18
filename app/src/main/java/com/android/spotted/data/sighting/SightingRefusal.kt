// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.sighting

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import java.io.FileNotFoundException

/** Firestore errors that mean the server refused the write: sending it again fails the same way. */
private val REFUSAL_CODES =
    setOf(
        Code.PERMISSION_DENIED,
        Code.INVALID_ARGUMENT,
        Code.FAILED_PRECONDITION,
        Code.NOT_FOUND,
        Code.ALREADY_EXISTS,
        Code.OUT_OF_RANGE,
    )

/**
 * True when sending a queued sighting failed with [error] for good, so it must be dropped instead
 * of sent again: the server refused it (Firestore rules or invalid data), or its photo copy is
 * missing or is not an image.
 *
 * Anything else (no network, server unavailable...) is false: it may work later. A sighting sent
 * while signed out or by another account is also refused with `PERMISSION_DENIED`, so
 * [com.android.spotted.model.sighting.PendingSightingSender] never sends those. The cause chain is
 * searched, as errors are sometimes wrapped (e.g. by `Tasks.await`).
 */
fun isSightingRefusal(error: Throwable): Boolean =
    generateSequence(error) { it.cause }
        .any {
          (it is FirebaseFirestoreException && it.code in REFUSAL_CODES) ||
              it is FileNotFoundException ||
              it is IllegalArgumentException
        }
