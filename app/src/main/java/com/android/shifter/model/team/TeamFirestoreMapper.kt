// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.team

import com.google.firebase.firestore.DocumentSnapshot
import com.swent.shifter.model.firestore.optionalInt
import com.swent.shifter.model.firestore.optionalString
import com.swent.shifter.model.firestore.requireInstant
import com.swent.shifter.model.firestore.requireParentEventId
import com.swent.shifter.model.firestore.requireString
import com.swent.shifter.model.firestore.requireStringList
import com.swent.shifter.model.firestore.toFirestoreTimestamp

/** Names this entity in the failure a malformed document raises. */
private const val ENTITY = "Team"

/**
 * Names of the teams subcollection and of every field of a team document.
 *
 * [Team.id] and [Team.eventId] are deliberately absent: they are carried by the document path
 * `/events/{eventId}/teams/{teamId}`, never by fields, so the path stays the single source of truth
 * for both.
 */
internal object TeamSchema {

  /** The `/events/{eventId}/teams/{teamId}` subcollection. */
  const val COLLECTION = "teams"

  const val NAME = "name"
  const val DESCRIPTION = "description"
  const val ICON = "icon"
  const val MANAGER_ID = "managerId"
  const val MEMBER_IDS = "memberIds"
  const val VOLUNTEERS_NEEDED = "volunteersNeeded"
  const val CREATED_AT = "createdAt"
}

/**
 * The document body written to `/events/{eventId}/teams/{teamId}`.
 *
 * [Team.managerId] is stored as the `/users` document id alone: copying the manager's profile here
 * would go stale the moment they rename themselves.
 */
internal fun Team.toFirestoreMap(): Map<String, Any?> =
    mapOf(
        TeamSchema.NAME to name,
        TeamSchema.DESCRIPTION to description,
        TeamSchema.ICON to icon,
        TeamSchema.MANAGER_ID to managerId,
        TeamSchema.MEMBER_IDS to memberIds,
        TeamSchema.VOLUNTEERS_NEEDED to volunteersNeeded,
        TeamSchema.CREATED_AT to createdAt.toFirestoreTimestamp(),
    )

/**
 * Rebuilds the [Team] stored in this document, taking [Team.id] from the document id and
 * [Team.eventId] from the id of the event document it is nested under.
 *
 * Throws [IllegalStateException] when a required field is missing or has an unexpected type: a
 * document that cannot be mapped is a schema problem, which must not be mistaken for "no team".
 * Hence [DocumentSnapshot.get] everywhere rather than `getString`/`getTimestamp`, which raise a
 * bare Firebase `RuntimeException` on a type mismatch.
 */
internal fun DocumentSnapshot.toTeam(): Team =
    Team(
        id = id,
        eventId = requireParentEventId(ENTITY),
        name = requireString(ENTITY, TeamSchema.NAME),
        description = requireString(ENTITY, TeamSchema.DESCRIPTION),
        icon = requireString(ENTITY, TeamSchema.ICON),
        managerId = optionalString(ENTITY, TeamSchema.MANAGER_ID),
        // Absent means nobody was assigned yet; a malformed entry fails rather than quietly
        // removing a volunteer from the team.
        memberIds = requireStringList(ENTITY, TeamSchema.MEMBER_IDS),
        // Absent means no staffing need has been set yet, which is how a team is created.
        volunteersNeeded = optionalInt(ENTITY, TeamSchema.VOLUNTEERS_NEEDED),
        createdAt = requireInstant(ENTITY, TeamSchema.CREATED_AT),
    )
