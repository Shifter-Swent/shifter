// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

import com.google.firebase.firestore.DocumentSnapshot
import com.swent.shifter.model.firestore.optionalString
import com.swent.shifter.model.firestore.requireInstant
import com.swent.shifter.model.firestore.requireInt
import com.swent.shifter.model.firestore.requireParentEventId
import com.swent.shifter.model.firestore.requireString
import com.swent.shifter.model.firestore.requireStringList
import com.swent.shifter.model.firestore.toFirestoreTimestamp

/** Names this entity in the failure a malformed document raises. */
private const val ENTITY = "Mission"

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
  const val TEAM_ID = "teamId"
  const val VOLUNTEERS_NEEDED = "volunteersNeeded"
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
        MissionSchema.TEAM_ID to teamId,
        MissionSchema.VOLUNTEERS_NEEDED to volunteersNeeded,
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
 * A team id of the wrong type is rejected too, rather than silently moving the mission to
 * "General".
 */
internal fun DocumentSnapshot.toMission(): Mission =
    Mission(
        id = id,
        eventId = requireParentEventId(ENTITY),
        title = requireString(ENTITY, MissionSchema.TITLE),
        description = requireString(ENTITY, MissionSchema.DESCRIPTION),
        teamId = optionalString(ENTITY, MissionSchema.TEAM_ID),
        volunteersNeeded = requireInt(ENTITY, MissionSchema.VOLUNTEERS_NEEDED),
        startAt = requireInstant(ENTITY, MissionSchema.START_AT),
        endAt = requireInstant(ENTITY, MissionSchema.END_AT),
        // Absent means nobody is assigned yet; a malformed entry fails rather than hiding the
        // mission from the person assigned to it.
        assigneeIds = requireStringList(ENTITY, MissionSchema.ASSIGNEE_IDS),
        createdAt = requireInstant(ENTITY, MissionSchema.CREATED_AT),
    )
