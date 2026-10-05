// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.android.shifter.model.event

import java.time.Instant

/**
 * An event organized on Shifter, which volunteers join with its [joinCode].
 *
 * The model stays free of Firebase types so it can be used by ViewModels and tested without a
 * backend.
 *
 * @property id the backend document id, empty until the event has been created.
 * @property organizerId id of the user who owns the event.
 * @property type the kind of event, used to filter and label it.
 * @property imageUrl cover picture of the event, or null when it has none.
 * @property startAt when the event starts, as a time-zone independent instant.
 * @property endAt when the event ends, as a time-zone independent instant.
 * @property emergencyContacts people a volunteer can reach during the event.
 * @property joinCode the code volunteers enter to join, empty until the backend assigns one.
 * @property status where the event stands in its lifecycle.
 * @property createdAt when the event was first persisted.
 */
data class Event(
    val id: String = "",
    val organizerId: String,
    val title: String,
    val description: String,
    val type: EventType,
    val imageUrl: String? = null,
    val startAt: Instant,
    val endAt: Instant,
    val location: EventLocation,
    val emergencyContacts: List<EmergencyContact> = emptyList(),
    val joinCode: String = "",
    val status: EventStatus = EventStatus.PREPARATION,
    val createdAt: Instant,
)

/**
 * Where an event takes place.
 *
 * Coordinates are plain doubles rather than a Firebase GeoPoint, so the domain model carries no
 * backend dependency.
 */
data class EventLocation(val address: String, val latitude: Double, val longitude: Double)

/**
 * Someone a volunteer can reach during an event.
 *
 * @property role what the contact is responsible for, or null when it is not specified.
 */
data class EmergencyContact(val name: String, val phoneNumber: String, val role: String? = null)

/** The kind of event an organizer runs. */
enum class EventType {
  MUSIC,
  MARKET,
  NATURE,
  FOOD,
  SPORT,
  PARTY,
  OTHER,
}

/** Lifecycle of an event, from preparation to archival. */
enum class EventStatus {
  PREPARATION,
  ONGOING,
  COMPLETED,
  ARCHIVED,
}
