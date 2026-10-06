// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.team

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.time.Instant

/**
 * Names of the teams subcollection and of every field of a team document.
 *
 * [Team.id] and the event id are deliberately absent: the first is the document id, the second the
 * id of the parent document, so the path already carries both and storing them would be a second
 * source of truth.
 */
internal object TeamSchema {

  /** The `/events/{eventId}/teams/{teamId}` subcollection. */
  const val COLLECTION = "teams"

  const val NAME = "name"
  const val ICON = "icon"
  const val MANAGER_ID = "managerId"
  const val VOLUNTEERS_NEEDED = "volunteersNeeded"
  const val CHECK_IN_ZONE = "checkInZone"
  const val CREATED_AT = "createdAt"

  /** Fields of the map stored under [CHECK_IN_ZONE]. */
  object CheckInZoneFields {
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
    const val RADIUS_METERS = "radiusMeters"
  }
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
        TeamSchema.ICON to icon,
        TeamSchema.MANAGER_ID to managerId,
        TeamSchema.VOLUNTEERS_NEEDED to volunteersNeeded,
        TeamSchema.CHECK_IN_ZONE to checkInZone?.toFirestoreMap(),
        TeamSchema.CREATED_AT to createdAt.toFirestoreTimestamp(),
    )

/**
 * Rebuilds the [Team] stored in this document, taking [Team.id] from the document id.
 *
 * Throws [IllegalStateException] when a required field is missing or has an unexpected type: a
 * document that cannot be mapped is a schema problem, which must not be mistaken for "no team".
 * Hence [DocumentSnapshot.get] everywhere rather than `getString`/`getTimestamp`, which raise a
 * bare Firebase `RuntimeException` on a type mismatch.
 */
internal fun DocumentSnapshot.toTeam(): Team =
    Team(
        id = id,
        name = requireString(TeamSchema.NAME),
        icon = requireString(TeamSchema.ICON),
        managerId = optionalString(TeamSchema.MANAGER_ID),
        volunteersNeeded = requireInt(TeamSchema.VOLUNTEERS_NEEDED),
        checkInZone = optionalCheckInZone(),
        createdAt = requireInstant(TeamSchema.CREATED_AT),
    )

private fun CheckInZone.toFirestoreMap(): Map<String, Any?> =
    mapOf(
        TeamSchema.CheckInZoneFields.LATITUDE to latitude,
        TeamSchema.CheckInZoneFields.LONGITUDE to longitude,
        TeamSchema.CheckInZoneFields.RADIUS_METERS to radiusMeters,
    )

/**
 * An absent or null field means the organizer has not placed the zone yet. A zone that is there is
 * mapped in full: a partial one would check volunteers in at the wrong place, or over the whole
 * event, so it is rejected rather than completed with a default.
 *
 * Any [Number] is accepted for the three values, because a whole-degree coordinate or a whole-metre
 * radius comes back from Firestore as a Long rather than as a Double.
 */
private fun DocumentSnapshot.optionalCheckInZone(): CheckInZone? {
  val value = get(TeamSchema.CHECK_IN_ZONE) ?: return null
  val zone = value as? Map<*, *> ?: invalid(TeamSchema.CHECK_IN_ZONE)
  return CheckInZone(
      latitude = requireZoneValue(zone, TeamSchema.CheckInZoneFields.LATITUDE),
      longitude = requireZoneValue(zone, TeamSchema.CheckInZoneFields.LONGITUDE),
      radiusMeters = requireZoneValue(zone, TeamSchema.CheckInZoneFields.RADIUS_METERS),
  )
}

private fun DocumentSnapshot.requireZoneValue(zone: Map<*, *>, field: String): Double =
    (zone[field] as? Number)?.toDouble() ?: invalid(TeamSchema.CHECK_IN_ZONE)

/**
 * Firestore stores whole numbers as Longs and fractional ones as Doubles, both wider than [Int], so
 * the count is read as a [Number] and narrowed here. A value the narrowing would change is rejected
 * rather than silently repaired: truncating `3.5`, or wrapping a count beyond [Int.MAX_VALUE] round
 * to a negative one, would staff the team with a number nobody wrote.
 */
private fun DocumentSnapshot.requireInt(field: String): Int {
  val value = get(field) as? Number ?: invalid(field)
  val whole = value.toLong()
  // Rejects a fractional, infinite or NaN value, then one that does not fit in an Int.
  if (whole.toDouble() != value.toDouble() || whole != whole.toInt().toLong()) invalid(field)
  return whole.toInt()
}

private fun DocumentSnapshot.requireString(field: String): String =
    get(field) as? String ?: invalid(field)

private fun DocumentSnapshot.requireInstant(field: String): Instant =
    (get(field) as? Timestamp)?.toInstant() ?: invalid(field)

/**
 * Absent and null both mean the team has no manager. A present value of another type is rejected
 * instead: mapping it to null would turn a schema problem into "this team has no manager".
 */
private fun DocumentSnapshot.optionalString(field: String): String? =
    when (val value = get(field)) {
      null -> null
      is String -> value
      else -> invalid(field)
    }

private fun DocumentSnapshot.invalid(field: String): Nothing =
    throw IllegalStateException("Team document '$id' has a missing or invalid '$field' field")

/** Firestore keeps microsecond precision, so a sub-microsecond [Instant] is truncated on write. */
private fun Instant.toFirestoreTimestamp(): Timestamp = Timestamp(epochSecond, nano)
