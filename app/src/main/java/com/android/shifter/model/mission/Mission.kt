// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

import java.time.Instant

/**
 * A mission of an event: a task with a time slot that needs a number of volunteers, such as sorting
 * the collected food between 14:00 and 15:30.
 *
 * The model stays free of Firebase types so it can be used by ViewModels and tested without a
 * backend.
 *
 * @property id the backend document id, empty until the mission has been created.
 * @property eventId id of the event the mission belongs to.
 * @property teamId id of the event team (shown as "pôle" in the app) the mission is attached to, or
 *   null for "General", the missions attached to no team. Teams are defined by the organizer when
 *   creating the event.
 * @property volunteersNeeded how many people the mission needs.
 * @property startAt when the mission starts, as a time-zone independent instant.
 * @property endAt when the mission ends, as a time-zone independent instant.
 * @property location where the mission takes place, or null for a mission with no fixed place. A
 *   mission with a location is called GeoTimed.
 * @property assigneeIds ids of the people assigned to the mission, possibly empty.
 * @property createdAt when the mission was first persisted.
 */
data class Mission(
    val id: String = "",
    val eventId: String,
    val title: String,
    val description: String,
    val teamId: String? = null,
    val volunteersNeeded: Int,
    val startAt: Instant,
    val endAt: Instant,
    val location: MissionLocation? = null,
    val assigneeIds: List<String> = emptyList(),
    val createdAt: Instant,
) {

  /** Whether the mission has a location, which is what puts it on the map. */
  val isGeoTimed: Boolean
    get() = location != null

  /**
   * Whether this mission and [other] cannot both be scheduled: they are GeoTimed missions of the
   * same team, in the same event, whose time slots overlap.
   *
   * Adjacent slots, where one mission ends exactly when the other starts, do not overlap. Missions
   * of no team ("General") never conflict, since there is no team whose schedule they would share.
   */
  fun conflictsWith(other: Mission): Boolean =
      isGeoTimed &&
          other.isGeoTimed &&
          teamId != null &&
          teamId == other.teamId &&
          eventId == other.eventId &&
          startAt < other.endAt &&
          other.startAt < endAt
}

/**
 * Where a GeoTimed mission takes place.
 *
 * Unlike an event's location, both coordinates are required: a mission only has a location to be
 * shown on the map and to check volunteers in, which both need a point. Coordinates are plain
 * doubles rather than a Firebase GeoPoint, so the domain model carries no backend dependency.
 *
 * @property address the address shown to the volunteers, never blank.
 * @property latitude in degrees, between -90 and 90.
 * @property longitude in degrees, between -180 and 180.
 */
data class MissionLocation(val address: String, val latitude: Double, val longitude: Double) {
  init {
    require(address.isNotBlank()) { "Address must not be blank" }
    require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90, but was $latitude" }
    require(longitude in -180.0..180.0) {
      "Longitude must be between -180 and 180, but was $longitude"
    }
  }
}
