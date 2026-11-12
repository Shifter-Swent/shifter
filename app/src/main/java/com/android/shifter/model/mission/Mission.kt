// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

import com.swent.shifter.model.location.GeoPosition
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
 * @property completed whether the mission was closed by hand before its end; see [isCompleted].
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
    val completed: Boolean = false,
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

  /**
   * This mission with the values [MissionRepository.updateMission] saves taken from [edited]: its
   * title, description, schedule, location and volunteers needed. Everything else, including the
   * team and the assignees, is kept.
   */
  internal fun withEditableFieldsOf(edited: Mission): Mission =
      copy(
          title = edited.title,
          description = edited.description,
          volunteersNeeded = edited.volunteersNeeded,
          startAt = edited.startAt,
          endAt = edited.endAt,
          location = edited.location,
      )
}

/**
 * Checks the input of [MissionRepository.updateMission] that needs no stored data, so every
 * implementation rejects it the same way, with an [IllegalArgumentException].
 */
internal fun requireValidUpdate(mission: Mission) {
  require(mission.eventId.isNotEmpty()) { "A mission must belong to an event" }
  require(mission.id.isNotEmpty()) { "Only a created mission can be updated" }
  require(mission.startAt < mission.endAt) { "A mission must end after it starts" }
  require(mission.volunteersNeeded >= 1) { "A mission needs at least one volunteer" }
}

/**
 * Throws [MissionCompletedException] when the [stored] mission is completed at [now], which
 * [MissionRepository.updateMission] refuses to change.
 */
internal fun requireNotCompleted(stored: Mission, now: Instant) {
  if (isCompleted(stored.completed, stored.endAt, now)) throw MissionCompletedException(stored)
}

/**
 * Throws [VolunteersBelowAssigneesException] when [edited] needs fewer volunteers than the people
 * already assigned to the [stored] mission.
 */
internal fun requireRoomForAssignees(stored: Mission, edited: Mission) {
  if (edited.volunteersNeeded < stored.assigneeIds.size) {
    throw VolunteersBelowAssigneesException(stored.assigneeIds.size)
  }
}

/**
 * Whether a mission is completed at [now]: it was closed by hand ([completed]), or its end at
 * [endAt] has passed.
 *
 * It takes the values rather than a [Mission] so a screen holding its own model of one reads the
 * same rule.
 */
fun isCompleted(completed: Boolean, endAt: Instant, now: Instant): Boolean =
    completed || now >= endAt

/**
 * Where a GeoTimed mission takes place.
 *
 * Unlike an event's location, the position is required: a mission only has a location to be shown
 * on the map and to check volunteers in, which both need a point. It is a [GeoPosition] rather than
 * a Firebase GeoPoint, so the domain model carries no backend dependency.
 *
 * @property address the address shown to the volunteers, never blank.
 * @property position the point the mission takes place at.
 */
data class MissionLocation(val address: String, val position: GeoPosition) {
  init {
    require(address.isNotBlank()) { "Address must not be blank" }
  }
}
