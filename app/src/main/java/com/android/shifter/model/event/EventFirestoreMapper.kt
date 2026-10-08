// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.event

import com.google.firebase.firestore.DocumentSnapshot
import com.swent.shifter.model.firestore.invalidField
import com.swent.shifter.model.firestore.optionalString
import com.swent.shifter.model.firestore.requireInstant
import com.swent.shifter.model.firestore.requireString
import com.swent.shifter.model.firestore.toFirestoreTimestamp

/** Names this entity in the failure a malformed document raises. */
private const val ENTITY = "Event"

/**
 * Names of the events collection and of every field of an event document.
 *
 * Every Firestore string used by [FirestoreEventRepository] comes from here, so renaming a field is
 * a single edit. [Event.id] is deliberately absent: the id is the document id, never a field.
 */
internal object EventSchema {

  /** The `/events/{eventId}` collection. */
  const val COLLECTION = "events"

  const val ORGANIZER_ID = "organizerId"
  const val TITLE = "title"
  const val DESCRIPTION = "description"
  const val TYPE = "type"
  const val IMAGE_URL = "imageUrl"
  const val START_AT = "startAt"
  const val END_AT = "endAt"
  const val LOCATION = "location"
  const val EMERGENCY_CONTACTS = "emergencyContacts"
  const val MEMBER_IDS = "memberIds"
  const val JOIN_CODE = "joinCode"
  const val STATUS = "status"
  const val CREATED_AT = "createdAt"

  /** Fields of the map stored under [LOCATION]. */
  object LocationFields {
    const val ADDRESS = "address"
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
  }

  /** Fields of each map in the [EMERGENCY_CONTACTS] array. */
  object ContactFields {
    const val NAME = "name"
    const val PHONE_NUMBER = "phoneNumber"
    const val ROLE = "role"
  }
}

/**
 * The document body written to `/events/{eventId}`.
 *
 * [Event.id] is not part of it: the id is carried by the document itself.
 */
internal fun Event.toFirestoreMap(): Map<String, Any?> =
    mapOf(
        EventSchema.ORGANIZER_ID to organizerId,
        EventSchema.TITLE to title,
        EventSchema.DESCRIPTION to description,
        EventSchema.TYPE to type.name,
        EventSchema.IMAGE_URL to imageUrl,
        EventSchema.START_AT to startAt.toFirestoreTimestamp(),
        EventSchema.END_AT to endAt.toFirestoreTimestamp(),
        EventSchema.LOCATION to location.toFirestoreMap(),
        EventSchema.EMERGENCY_CONTACTS to emergencyContacts.map { it.toFirestoreMap() },
        EventSchema.MEMBER_IDS to memberIds,
        EventSchema.JOIN_CODE to joinCode,
        EventSchema.STATUS to status.name,
        EventSchema.CREATED_AT to createdAt.toFirestoreTimestamp(),
    )

/**
 * Rebuilds the [Event] stored in this document, taking [Event.id] from the document id.
 *
 * Throws [IllegalStateException] when a required field is missing or has an unexpected type: a
 * document that cannot be mapped is a schema problem, which must not be mistaken for "no event".
 * Every field is read through [DocumentSnapshot.get] rather than through the typed `getString` and
 * `getTimestamp` accessors, which raise a bare Firebase `RuntimeException` on a type mismatch and
 * would therefore break that contract.
 */
internal fun DocumentSnapshot.toEvent(): Event =
    Event(
        id = id,
        organizerId = requireString(ENTITY, EventSchema.ORGANIZER_ID),
        title = requireString(ENTITY, EventSchema.TITLE),
        description = requireString(ENTITY, EventSchema.DESCRIPTION),
        type = requireEventType(),
        imageUrl = optionalString(ENTITY, EventSchema.IMAGE_URL),
        startAt = requireInstant(ENTITY, EventSchema.START_AT),
        endAt = requireInstant(ENTITY, EventSchema.END_AT),
        location = requireLocation(),
        emergencyContacts = requireEmergencyContacts(),
        memberIds = requireMemberIds(),
        joinCode = requireString(ENTITY, EventSchema.JOIN_CODE),
        status = requireEventStatus(),
        createdAt = requireInstant(ENTITY, EventSchema.CREATED_AT),
    )

private fun EventLocation.toFirestoreMap(): Map<String, Any?> =
    mapOf(
        EventSchema.LocationFields.ADDRESS to address,
        EventSchema.LocationFields.LATITUDE to latitude,
        EventSchema.LocationFields.LONGITUDE to longitude,
    )

private fun EmergencyContact.toFirestoreMap(): Map<String, Any?> =
    mapOf(
        EventSchema.ContactFields.NAME to name,
        EventSchema.ContactFields.PHONE_NUMBER to phoneNumber,
        EventSchema.ContactFields.ROLE to role,
    )

/**
 * An unknown name maps to [EventType.OTHER]: the type only labels and filters an event, so a type
 * added by a newer build must not stop an older build from reading the same Firestore. A missing or
 * non-string field stays a schema problem.
 */
private fun DocumentSnapshot.requireEventType(): EventType {
  val name = requireString(ENTITY, EventSchema.TYPE)
  return EventType.entries.firstOrNull { it.name == name } ?: EventType.OTHER
}

/**
 * Unlike [requireEventType], an unknown name is rejected: the status decides what the app allows on
 * an event and [EventStatus] has no catch-all member, so guessing one would either resurrect an
 * archived event or archive a live one.
 */
private fun DocumentSnapshot.requireEventStatus(): EventStatus {
  val name = requireString(ENTITY, EventSchema.STATUS)
  return EventStatus.entries.firstOrNull { it.name == name }
      ?: invalidField(ENTITY, EventSchema.STATUS)
}

private fun DocumentSnapshot.requireLocation(): EventLocation {
  val location =
      get(EventSchema.LOCATION) as? Map<*, *> ?: invalidField(ENTITY, EventSchema.LOCATION)
  val latitude = requireCoordinate(location, EventSchema.LocationFields.LATITUDE)
  val longitude = requireCoordinate(location, EventSchema.LocationFields.LONGITUDE)
  // A map pin needs both coordinates, so half a pair is a malformed document rather than an event
  // waiting to be geocoded.
  if ((latitude == null) != (longitude == null)) invalidField(ENTITY, EventSchema.LOCATION)
  return EventLocation(
      address =
          location[EventSchema.LocationFields.ADDRESS] as? String
              ?: invalidField(ENTITY, EventSchema.LOCATION),
      latitude = latitude,
      longitude = longitude,
  )
}

/**
 * An absent or null coordinate means the event has not been geocoded yet; a present one of another
 * type is rejected. Any [Number] is accepted, because a whole-degree coordinate written by hand
 * comes back from Firestore as a Long rather than as a Double.
 */
private fun DocumentSnapshot.requireCoordinate(location: Map<*, *>, field: String): Double? =
    when (val value = location[field]) {
      null -> null
      is Number -> value.toDouble()
      else -> invalidField(ENTITY, EventSchema.LOCATION)
    }

/**
 * An absent field means the event has no emergency contact. A present but malformed entry is
 * rejected rather than skipped: silently dropping an emergency contact would be a safety problem.
 */
private fun DocumentSnapshot.requireEmergencyContacts(): List<EmergencyContact> {
  val field = get(EventSchema.EMERGENCY_CONTACTS) ?: return emptyList()
  val entries = field as? List<*> ?: invalidField(ENTITY, EventSchema.EMERGENCY_CONTACTS)
  return entries.map { entry ->
    val contact = entry as? Map<*, *> ?: invalidField(ENTITY, EventSchema.EMERGENCY_CONTACTS)
    EmergencyContact(
        name =
            contact[EventSchema.ContactFields.NAME] as? String
                ?: invalidField(ENTITY, EventSchema.EMERGENCY_CONTACTS),
        phoneNumber =
            contact[EventSchema.ContactFields.PHONE_NUMBER] as? String
                ?: invalidField(ENTITY, EventSchema.EMERGENCY_CONTACTS),
        role = contact[EventSchema.ContactFields.ROLE] as? String,
    )
  }
}

/**
 * An absent field means nobody joined the event yet. A malformed entry is rejected rather than
 * skipped: dropping a member would hide the event from the volunteer who joined it.
 */
private fun DocumentSnapshot.requireMemberIds(): List<String> {
  val field = get(EventSchema.MEMBER_IDS) ?: return emptyList()
  val entries = field as? List<*> ?: invalidField(ENTITY, EventSchema.MEMBER_IDS)
  return entries.map { it as? String ?: invalidField(ENTITY, EventSchema.MEMBER_IDS) }
}
