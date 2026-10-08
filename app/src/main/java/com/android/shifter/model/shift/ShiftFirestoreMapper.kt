// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.shift

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.time.Instant

/**
 * Names of the shifts subcollection and of every field of a shift document.
 *
 * [Shift.id] and the event id are deliberately absent: the first is the document id, the second the
 * id of the parent document, so the path already carries both and storing them would be a second
 * source of truth.
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
 * Rebuilds the [Shift] stored in this document, taking [Shift.id] from the document id.
 *
 * Throws [IllegalStateException] when a required field is missing or has an unexpected type: a
 * document that cannot be mapped is a schema problem, which must not be mistaken for "no shift".
 * Hence [DocumentSnapshot.get] everywhere rather than `getString`/`getTimestamp`, which raise a
 * bare Firebase `RuntimeException` on a type mismatch.
 */
internal fun DocumentSnapshot.toShift(): Shift =
    Shift(
        id = id,
        teamId = requireString(ShiftSchema.TEAM_ID),
        assigneeIds = requireAssigneeIds(),
        startAt = requireInstant(ShiftSchema.START_AT),
        endAt = requireInstant(ShiftSchema.END_AT),
        createdAt = requireInstant(ShiftSchema.CREATED_AT),
    )

/**
 * An absent field means the shift has no assignees yet and maps to an empty list.
 *
 * An explicit null is rejected: a non-nullable list never serializes to null, so null is a schema
 * problem rather than an empty shift.
 *
 * Order is preserved as stored, and malformed entries are rejected rather than skipped.
 */
private fun DocumentSnapshot.requireAssigneeIds(): List<String> {
  if (!contains(ShiftSchema.ASSIGNEE_IDS)) return emptyList()
  val entries = get(ShiftSchema.ASSIGNEE_IDS) as? List<*> ?: invalid(ShiftSchema.ASSIGNEE_IDS)
  return entries.map { it as? String ?: invalid(ShiftSchema.ASSIGNEE_IDS) }
}

private fun DocumentSnapshot.requireString(field: String): String =
    get(field) as? String ?: invalid(field)

private fun DocumentSnapshot.requireInstant(field: String): Instant =
    (get(field) as? Timestamp)?.toInstant() ?: invalid(field)

private fun DocumentSnapshot.invalid(field: String): Nothing =
    throw IllegalStateException("Shift document '$id' has a missing or invalid '$field' field")

/** Firestore keeps microsecond precision, so a sub-microsecond [Instant] is truncated on write. */
private fun Instant.toFirestoreTimestamp(): Timestamp = Timestamp(epochSecond, nano)
