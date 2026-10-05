// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.event

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.time.Instant

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
        EventSchema.JOIN_CODE to joinCode,
        EventSchema.STATUS to status.name,
        EventSchema.CREATED_AT to createdAt.toFirestoreTimestamp(),
    )

/**
 * Rebuilds the [Event] stored in this document, taking [Event.id] from the document id.
 *
 * Throws [IllegalStateException] when a required field is missing or has an unexpected type: a
 * document that cannot be mapped is a schema problem, which must not be mistaken for "no event".
 */
internal fun DocumentSnapshot.toEvent(): Event =
    Event(
        id = id,
        organizerId = requireString(EventSchema.ORGANIZER_ID),
        title = requireString(EventSchema.TITLE),
        description = requireString(EventSchema.DESCRIPTION),
        type =
            EventType.entries.firstOrNull { it.name == requireString(EventSchema.TYPE) }
                ?: invalid(EventSchema.TYPE),
        imageUrl = getString(EventSchema.IMAGE_URL),
        startAt = requireInstant(EventSchema.START_AT),
        endAt = requireInstant(EventSchema.END_AT),
        location = requireLocation(),
        emergencyContacts = requireEmergencyContacts(),
        joinCode = requireString(EventSchema.JOIN_CODE),
        status =
            EventStatus.entries.firstOrNull { it.name == requireString(EventSchema.STATUS) }
                ?: invalid(EventSchema.STATUS),
        createdAt = requireInstant(EventSchema.CREATED_AT),
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

private fun DocumentSnapshot.requireLocation(): EventLocation {
  val location = get(EventSchema.LOCATION) as? Map<*, *> ?: invalid(EventSchema.LOCATION)
  return EventLocation(
      address =
          location[EventSchema.LocationFields.ADDRESS] as? String ?: invalid(EventSchema.LOCATION),
      latitude =
          (location[EventSchema.LocationFields.LATITUDE] as? Number)?.toDouble()
              ?: invalid(EventSchema.LOCATION),
      longitude =
          (location[EventSchema.LocationFields.LONGITUDE] as? Number)?.toDouble()
              ?: invalid(EventSchema.LOCATION),
  )
}

/**
 * An absent field means the event has no emergency contact. A present but malformed entry is
 * rejected rather than skipped: silently dropping an emergency contact would be a safety problem.
 */
private fun DocumentSnapshot.requireEmergencyContacts(): List<EmergencyContact> {
  val field = get(EventSchema.EMERGENCY_CONTACTS) ?: return emptyList()
  val entries = field as? List<*> ?: invalid(EventSchema.EMERGENCY_CONTACTS)
  return entries.map { entry ->
    val contact = entry as? Map<*, *> ?: invalid(EventSchema.EMERGENCY_CONTACTS)
    EmergencyContact(
        name =
            contact[EventSchema.ContactFields.NAME] as? String
                ?: invalid(EventSchema.EMERGENCY_CONTACTS),
        phoneNumber =
            contact[EventSchema.ContactFields.PHONE_NUMBER] as? String
                ?: invalid(EventSchema.EMERGENCY_CONTACTS),
        role = contact[EventSchema.ContactFields.ROLE] as? String,
    )
  }
}

private fun DocumentSnapshot.requireString(field: String): String =
    getString(field) ?: invalid(field)

private fun DocumentSnapshot.requireInstant(field: String): Instant =
    getTimestamp(field)?.toInstant() ?: invalid(field)

private fun DocumentSnapshot.invalid(field: String): Nothing =
    throw IllegalStateException("Event document '$id' has a missing or invalid '$field' field")

/** Firestore keeps microsecond precision, so a sub-microsecond [Instant] is truncated on write. */
private fun Instant.toFirestoreTimestamp(): Timestamp = Timestamp(epochSecond, nano)
