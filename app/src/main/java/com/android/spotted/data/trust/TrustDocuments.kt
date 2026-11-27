// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.data.trust

import com.android.spotted.model.trust.TrustScore
import com.android.spotted.model.trust.Verdict
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction

/**
 * How a [TrustScore] is stored in `trust/{uid}`, and the write that applies a verdict to it. Shared
 * by [TrustRepositoryFirestore] (the owner's verdicts) and the vote repository (the votes' false
 * verdict), so both write exactly what the Firestore rules check.
 */
internal object TrustDocuments {
  const val TRUST_COLLECTION = "trust"
  const val SCORE_FIELD = "score"
  const val JUDGED_FIELD = "judged"
  const val FALSE_SIGHTINGS_FIELD = "falseSightings"
  const val PENALTIES_APPLIED_FIELD = "penaltiesApplied"
  const val LAST_SIGHTING_ID_FIELD = "lastSightingId"

  /** On a sighting: the verdict that counted for its reporter, so it counts once. */
  const val TRUST_VERDICT_FIELD = "trustVerdict"

  /**
   * On a sighting judged false: the trust of its reporter just before the penalty, as a map of the
   * [TrustScore] fields. A restore needs it to cancel the penalty.
   */
  const val PENALTY_FIELD = "penalty"

  const val REPORTER_ID_FIELD = "reporterId"

  /** The fields of [trust], as stored in `trust/{uid}` and in a sighting's [PENALTY_FIELD]. */
  fun fieldsOf(trust: TrustScore): Map<String, Any> =
      mapOf(
          SCORE_FIELD to trust.score,
          JUDGED_FIELD to trust.judged,
          FALSE_SIGHTINGS_FIELD to trust.falseSightings,
          PENALTIES_APPLIED_FIELD to trust.penaltiesApplied,
      )

  /** The stored trust, or [TrustScore.INITIAL] if there is no document. */
  fun DocumentSnapshot.toTrustScore(): TrustScore {
    if (!exists()) return TrustScore.INITIAL
    return checkNotNull(data?.toTrustScore()) { "trust/$id is not a trust score" }
  }

  /** The [TrustScore] stored in this map ([fieldsOf]), or null if a required field is missing. */
  fun Map<*, *>.toTrustScore(): TrustScore? {
    val score = (this[SCORE_FIELD] as? Number)?.toDouble() ?: return null
    val judged = (this[JUDGED_FIELD] as? Number)?.toInt() ?: return null
    val falseSightings = (this[FALSE_SIGHTINGS_FIELD] as? Number)?.toInt() ?: 0
    val penaltiesApplied = (this[PENALTIES_APPLIED_FIELD] as? Number)?.toInt() ?: falseSightings
    return TrustScore(score, judged, falseSightings, penaltiesApplied)
  }

  /** The id of the reporter of [sighting]. */
  fun reporterIdOf(sighting: DocumentSnapshot): String =
      checkNotNull(sighting.getString(REPORTER_ID_FIELD)) {
        "sightings/${sighting.id} has no reporterId"
      }

  /** Writes [trust] as the trust of [uid], changed last by the sighting [sightingId]. */
  fun Transaction.setTrust(
      firestore: FirebaseFirestore,
      uid: String,
      trust: TrustScore,
      sightingId: String,
  ) {
    set(
        firestore.collection(TRUST_COLLECTION).document(uid),
        fieldsOf(trust) + (LAST_SIGHTING_ID_FIELD to sightingId),
    )
  }

  /**
   * Applies [verdict] to the trust of the reporter of [sighting], and marks the sighting judged
   * with it, together with the [otherSightingFields]. A false verdict also saves on the sighting
   * the trust before it ([PENALTY_FIELD]). Reads before it writes: call it before the other writes
   * of the transaction. Returns the new trust.
   */
  fun Transaction.judge(
      firestore: FirebaseFirestore,
      sighting: DocumentSnapshot,
      verdict: Verdict,
      otherSightingFields: Map<String, Any>,
  ): TrustScore {
    val reporterId = reporterIdOf(sighting)
    val before = get(firestore.collection(TRUST_COLLECTION).document(reporterId)).toTrustScore()
    val next = before.after(verdict)
    setTrust(firestore, reporterId, next, sighting.id)
    val penalty =
        if (verdict == Verdict.FALSE) mapOf(PENALTY_FIELD to fieldsOf(before)) else emptyMap()
    update(
        sighting.reference,
        otherSightingFields + penalty + (TRUST_VERDICT_FIELD to verdict.name),
    )
    return next
  }
}
