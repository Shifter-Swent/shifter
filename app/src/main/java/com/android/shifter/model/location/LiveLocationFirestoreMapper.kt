// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import com.google.firebase.firestore.DocumentSnapshot
import com.swent.shifter.model.firestore.invalidField
import com.swent.shifter.model.firestore.requireInstant
import com.swent.shifter.model.firestore.toFirestoreTimestamp
import java.time.Instant

/** Names this entity in the failure a malformed document raises. */
private const val ENTITY = "LiveLocation"

/**
 * Names of the live locations subcollection and of every field of a shared position.
 *
 * [LiveLocation.userId] is deliberately absent, and so are the event and the team: they are carried
 * by the document path `/events/{eventId}/teams/{teamId}/liveLocations/{userId}`. One position per
 * volunteer and team follows from the document id being their uid.
 */
internal object LiveLocationSchema {

  /** The `liveLocations` subcollection of each `/events/{eventId}/teams/{teamId}` document. */
  const val COLLECTION = "liveLocations"

  const val POSITION = "position"
  const val MEASURED_AT = "measuredAt"

  /** Fields of the [POSITION] map. */
  object PositionFields {
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
  }
}

/**
 * The document body of [position], measured at [measuredAt]. Whose position it is stays out of it:
 * the document id carries the uid, which is why this takes the two values rather than a
 * [LiveLocation].
 *
 * [measuredAt] is written as the device measured it, not as a server timestamp: `firestore.rules`
 * accept it precisely so a position taken offline keeps the time it was taken at.
 */
internal fun liveLocationFields(position: GeoPosition, measuredAt: Instant): Map<String, Any> =
    mapOf(
        LiveLocationSchema.POSITION to
            mapOf(
                LiveLocationSchema.PositionFields.LATITUDE to position.latitude,
                LiveLocationSchema.PositionFields.LONGITUDE to position.longitude,
            ),
        LiveLocationSchema.MEASURED_AT to measuredAt.toFirestoreTimestamp(),
    )

/**
 * Rebuilds the [LiveLocation] stored in this document, taking [LiveLocation.userId] from the
 * document id.
 *
 * Throws [IllegalStateException] when a field is missing or has an unexpected type.
 */
internal fun DocumentSnapshot.toLiveLocation(): LiveLocation =
    LiveLocation(
        userId = id,
        position = requirePosition(),
        measuredAt = requireInstant(ENTITY, LiveLocationSchema.MEASURED_AT),
    )

/**
 * Both coordinates are required: half a pair is no position at all. A value out of range is
 * rejected by [GeoPosition] itself, reported here as the malformed document it comes from rather
 * than as the caller's bad argument.
 */
private fun DocumentSnapshot.requirePosition(): GeoPosition {
  val position =
      get(LiveLocationSchema.POSITION) as? Map<*, *>
          ?: invalidField(ENTITY, LiveLocationSchema.POSITION)
  return try {
    GeoPosition(
        latitude = position.requireCoordinate(LiveLocationSchema.PositionFields.LATITUDE),
        longitude = position.requireCoordinate(LiveLocationSchema.PositionFields.LONGITUDE),
    )
  } catch (e: IllegalArgumentException) {
    throw IllegalStateException(
        "$ENTITY document '$id' has a missing or invalid '${LiveLocationSchema.POSITION}' field",
        e,
    )
  }
}

/**
 * Any [Number] is accepted, because a whole-degree coordinate written by hand comes back from
 * Firestore as a Long rather than as a Double.
 */
private fun Map<*, *>.requireCoordinate(field: String): Double =
    (this[field] as? Number)?.toDouble()
        ?: throw IllegalArgumentException("missing or invalid '$field'")
