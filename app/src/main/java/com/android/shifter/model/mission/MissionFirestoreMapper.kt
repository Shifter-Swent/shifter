// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

import com.google.firebase.firestore.DocumentSnapshot
import com.swent.shifter.model.firestore.invalidField
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
  const val LOCATION = "location"
  const val ASSIGNEE_IDS = "assigneeIds"
  const val CREATED_AT = "createdAt"

  /** Fields of the [LOCATION] map. */
  object LocationFields {
    const val ADDRESS = "address"
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
  }
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
        MissionSchema.LOCATION to location?.toFirestoreMap(),
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
        location = optionalLocation(),
        // Absent means nobody is assigned yet; a malformed entry fails rather than hiding the
        // mission from the person assigned to it.
        assigneeIds = requireStringList(ENTITY, MissionSchema.ASSIGNEE_IDS),
        createdAt = requireInstant(ENTITY, MissionSchema.CREATED_AT),
    )

private fun MissionLocation.toFirestoreMap(): Map<String, Any?> =
    mapOf(
        MissionSchema.LocationFields.ADDRESS to address,
        MissionSchema.LocationFields.LATITUDE to latitude,
        MissionSchema.LocationFields.LONGITUDE to longitude,
    )

/**
 * An absent or null field means the mission has no location. A location that is there is mapped in
 * full: half a location would put the mission on the map at a wrong point, or in no place at all,
 * so it is rejected rather than completed with a default, as are coordinates out of range.
 *
 * Any [Number] is accepted for the coordinates, because a whole-degree coordinate comes back from
 * Firestore as a Long rather than as a Double.
 */
private fun DocumentSnapshot.optionalLocation(): MissionLocation? {
  val value = get(MissionSchema.LOCATION) ?: return null
  val location = value as? Map<*, *> ?: invalidField(ENTITY, MissionSchema.LOCATION)
  val address =
      location[MissionSchema.LocationFields.ADDRESS] as? String
          ?: invalidField(ENTITY, MissionSchema.LOCATION)
  val latitude = requireCoordinate(location, MissionSchema.LocationFields.LATITUDE)
  val longitude = requireCoordinate(location, MissionSchema.LocationFields.LONGITUDE)
  return runCatching { MissionLocation(address, latitude, longitude) }
      .getOrElse { invalidField(ENTITY, MissionSchema.LOCATION) }
}

private fun DocumentSnapshot.requireCoordinate(location: Map<*, *>, field: String): Double =
    (location[field] as? Number)?.toDouble() ?: invalidField(ENTITY, MissionSchema.LOCATION)
