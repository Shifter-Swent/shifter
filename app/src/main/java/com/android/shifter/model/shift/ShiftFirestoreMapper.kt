// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.shift

import com.google.firebase.firestore.DocumentSnapshot
import com.swent.shifter.model.firestore.requireInstant
import com.swent.shifter.model.firestore.requireParentEventId
import com.swent.shifter.model.firestore.requireString
import com.swent.shifter.model.firestore.requireStringList
import com.swent.shifter.model.firestore.toFirestoreTimestamp

/** Names this entity in the failure a malformed document raises. */
private const val ENTITY = "Shift"

/**
 * Names of the shifts subcollection and of every field of a shift document.
 *
 * [Shift.id] and [Shift.eventId] are deliberately absent: they are carried by the document path
 * `/events/{eventId}/shifts/{shiftId}`, never by fields, so the path stays the single source of
 * truth for both.
 */
internal object ShiftSchema {

  /** The `/events/{eventId}/shifts/{shiftId}` subcollection. */
  const val COLLECTION = "shifts"

  const val TEAM_ID = "teamId"
  const val ASSIGNEE_IDS = "assigneeIds"
  const val START_AT = "startAt"
  const val END_AT = "endAt"
  const val CREATED_AT = "createdAt"
}

/** The document body written to `/events/{eventId}/shifts/{shiftId}`. */
internal fun Shift.toFirestoreMap(): Map<String, Any?> =
    mapOf(
        ShiftSchema.TEAM_ID to teamId,
        ShiftSchema.ASSIGNEE_IDS to assigneeIds,
        ShiftSchema.START_AT to startAt.toFirestoreTimestamp(),
        ShiftSchema.END_AT to endAt.toFirestoreTimestamp(),
        ShiftSchema.CREATED_AT to createdAt.toFirestoreTimestamp(),
    )

/**
 * Rebuilds the [Shift] stored in this document, taking [Shift.id] from the document id and
 * [Shift.eventId] from the id of the event document it is nested under.
 *
 * Throws [IllegalStateException] when a required field is missing or has an unexpected type: a
 * document that cannot be mapped is a schema problem, which must not be mistaken for "no shift".
 * Hence [DocumentSnapshot.get] everywhere rather than `getString`/`getTimestamp`, which raise a
 * bare Firebase `RuntimeException` on a type mismatch.
 */
internal fun DocumentSnapshot.toShift(): Shift =
    Shift(
        id = id,
        eventId = requireParentEventId(ENTITY),
        teamId = requireString(ENTITY, ShiftSchema.TEAM_ID),
        // Absent means nobody is scheduled yet; a malformed entry fails rather than quietly
        // taking a volunteer off a shift they are due to work.
        assigneeIds = requireStringList(ENTITY, ShiftSchema.ASSIGNEE_IDS),
        startAt = requireInstant(ENTITY, ShiftSchema.START_AT),
        endAt = requireInstant(ENTITY, ShiftSchema.END_AT),
        createdAt = requireInstant(ENTITY, ShiftSchema.CREATED_AT),
    )
