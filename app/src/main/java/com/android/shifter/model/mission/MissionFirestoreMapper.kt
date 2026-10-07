// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.time.Instant

/**
 * Names of the missions subcollection and of every field of a mission document.
 *
 * Every Firestore string used by [FirestoreMissionRepository] comes from here, so renaming a field
 * is a single edit. [Mission.id] and [Mission.eventId] are deliberately absent: they are carried by
 * the document path `/events/{eventId}/missions/{missionId}`, never by fields.
 */
internal object MissionSchema {

  /** The `missions` subcollection of each `/events/{eventId}` document. */
  const val COLLECTION = "missions"

  const val TITLE = "title"
  const val DESCRIPTION = "description"
  const val SECTOR_ID = "sectorId"
  const val STAFF_NEEDED = "staffNeeded"
  const val START_AT = "startAt"
  const val END_AT = "endAt"
  const val ASSIGNEE_IDS = "assigneeIds"
  const val CREATED_AT = "createdAt"
}

/**
 * The document body written to `/events/{eventId}/missions/{missionId}`.
 *
 * [Mission.id] and [Mission.eventId] are not part of it: they are carried by the document path.
 */
internal fun Mission.toFirestoreMap(): Map<String, Any?> =
    mapOf(
        MissionSchema.TITLE to title,
        MissionSchema.DESCRIPTION to description,
        MissionSchema.SECTOR_ID to sectorId,
        MissionSchema.STAFF_NEEDED to staffNeeded,
        MissionSchema.START_AT to startAt.toFirestoreTimestamp(),
        MissionSchema.END_AT to endAt.toFirestoreTimestamp(),
        MissionSchema.ASSIGNEE_IDS to assigneeIds,
        MissionSchema.CREATED_AT to createdAt.toFirestoreTimestamp(),
    )

/**
 * Rebuilds the [Mission] stored in this document, taking [Mission.id] from the document id and
 * [Mission.eventId] from the id of the event document it is nested under.
 *
 * Throws [IllegalStateException] when a required field is missing or has an unexpected type: a
 * document that cannot be mapped is a schema problem, which must not be mistaken for "no mission".
 * Every field is read through [DocumentSnapshot.get] rather than through the typed accessors, which
 * raise a bare Firebase `RuntimeException` on a type mismatch and would therefore break that
 * contract.
 */
internal fun DocumentSnapshot.toMission(): Mission =
    Mission(
        id = id,
        eventId = requireEventId(),
        title = requireString(MissionSchema.TITLE),
        description = requireString(MissionSchema.DESCRIPTION),
        sectorId = optionalString(MissionSchema.SECTOR_ID),
        staffNeeded = requireStaffNeeded(),
        startAt = requireInstant(MissionSchema.START_AT),
        endAt = requireInstant(MissionSchema.END_AT),
        assigneeIds = requireAssigneeIds(),
        createdAt = requireInstant(MissionSchema.CREATED_AT),
    )

/** A mission document only exists under an event document, whose id is the mission's event id. */
private fun DocumentSnapshot.requireEventId(): String =
    reference.parent.parent?.id
        ?: throw IllegalStateException("Mission document '$id' is not nested under an event")

/**
 * Firestore stores every integer as a 64-bit Long, so the count comes back as a Long. A fractional
 * or out-of-range value is rejected rather than rounded: a wrong staff count would be shown to the
 * organizer as if it were the one they entered.
 */
private fun DocumentSnapshot.requireStaffNeeded(): Int {
  val value = get(MissionSchema.STAFF_NEEDED) as? Long ?: invalid(MissionSchema.STAFF_NEEDED)
  if (value !in Int.MIN_VALUE..Int.MAX_VALUE) invalid(MissionSchema.STAFF_NEEDED)
  return value.toInt()
}

/**
 * An absent field means nobody is assigned yet. A malformed entry is rejected rather than skipped:
 * dropping an assignee would hide the mission from the person assigned to it.
 */
private fun DocumentSnapshot.requireAssigneeIds(): List<String> {
  val field = get(MissionSchema.ASSIGNEE_IDS) ?: return emptyList()
  val entries = field as? List<*> ?: invalid(MissionSchema.ASSIGNEE_IDS)
  return entries.map { it as? String ?: invalid(MissionSchema.ASSIGNEE_IDS) }
}

private fun DocumentSnapshot.requireString(field: String): String =
    get(field) as? String ?: invalid(field)

private fun DocumentSnapshot.requireInstant(field: String): Instant =
    (get(field) as? Timestamp)?.toInstant() ?: invalid(field)

/**
 * An absent or null field means the mission has no value for it, which is why both map to null. A
 * present value of another type is rejected: mapping it to null would silently move the mission to
 * "General".
 */
private fun DocumentSnapshot.optionalString(field: String): String? =
    when (val value = get(field)) {
      null -> null
      is String -> value
      else -> invalid(field)
    }

private fun DocumentSnapshot.invalid(field: String): Nothing =
    throw IllegalStateException("Mission document '$id' has a missing or invalid '$field' field")

/** Firestore keeps microsecond precision, so a sub-microsecond [Instant] is truncated on write. */
private fun Instant.toFirestoreTimestamp(): Timestamp = Timestamp(epochSecond, nano)
