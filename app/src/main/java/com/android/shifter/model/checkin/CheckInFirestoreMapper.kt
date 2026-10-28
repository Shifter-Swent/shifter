// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.checkin

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.swent.shifter.model.firestore.invalidField
import com.swent.shifter.model.firestore.requireString

/** Names this entity in the failure a malformed document raises. */
private const val ENTITY = "CheckIn"

/**
 * Names of the check-ins subcollection and of every field of a check-in document.
 *
 * [CheckIn.eventId] and [CheckIn.missionId] are deliberately absent: they are carried by the
 * document path `/events/{eventId}/missions/{missionId}/checkIns/{userId}`. [USER_ID] repeats the
 * document id so a volunteer's check-ins can be queried across all missions.
 */
internal object CheckInSchema {

  /** The `checkIns` subcollection of each `/events/{eventId}/missions/{missionId}` document. */
  const val COLLECTION = "checkIns"

  const val USER_ID = "userId"
  const val METHOD = "method"
  const val CHECKED_IN_AT = "checkedInAt"
  const val CHECKED_IN_BY = "checkedInBy"
}

/**
 * The document body of a new check-in of [userId], recorded by [checkedInBy]. [CheckIn.checkedInAt]
 * is a server timestamp: `firestore.rules` require it to be the request time.
 */
internal fun newCheckInFields(
    userId: String,
    method: CheckInMethod,
    checkedInBy: String,
): Map<String, Any> =
    mapOf(
        CheckInSchema.USER_ID to userId,
        CheckInSchema.METHOD to method.name,
        CheckInSchema.CHECKED_IN_AT to FieldValue.serverTimestamp(),
        CheckInSchema.CHECKED_IN_BY to checkedInBy,
    )

/**
 * Rebuilds the [CheckIn] stored in this document, taking [CheckIn.userId] from the document id and
 * [CheckIn.missionId] and [CheckIn.eventId] from the documents it is nested under.
 *
 * A check-in whose write is still pending has no server time yet: it is read with an estimate of
 * it, as [CheckInRepository.observeMyCheckIns] promises.
 *
 * Throws [IllegalStateException] when a field is missing or has an unexpected type. An unknown
 * method is rejected too rather than read as a default: a check-in is a record of who was present
 * and how, which must not be guessed.
 */
internal fun DocumentSnapshot.toCheckIn(): CheckIn {
  val mission =
      reference.parent.parent
          ?: throw IllegalStateException("$ENTITY document '$id' is not nested under a mission")
  val event =
      mission.parent.parent
          ?: throw IllegalStateException("$ENTITY document '$id' is not nested under an event")
  val method = requireString(ENTITY, CheckInSchema.METHOD)
  return CheckIn(
      eventId = event.id,
      missionId = mission.id,
      userId = id,
      checkedInAt =
          (get(CheckInSchema.CHECKED_IN_AT, ServerTimestampBehavior.ESTIMATE) as? Timestamp)
              ?.toInstant() ?: invalidField(ENTITY, CheckInSchema.CHECKED_IN_AT),
      method =
          CheckInMethod.entries.firstOrNull { it.name == method }
              ?: invalidField(ENTITY, CheckInSchema.METHOD),
      checkedInBy = requireString(ENTITY, CheckInSchema.CHECKED_IN_BY),
  )
}
