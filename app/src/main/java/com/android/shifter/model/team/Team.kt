// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.team

import java.time.Instant

/**
 * A group of volunteers an organizer creates inside an event, led by a manager.
 *
 * The event is not a property: it is the parent document of the team.
 *
 * @property id the backend document id, empty until the team has been created.
 * @property icon identifier of the icon the team is shown with.
 * @property managerId id of the `/users` document of the manager, or null while the team has none:
 *   an organizer creates a team before appointing someone to lead it.
 * @property memberIds ids of the `/users` documents of the volunteers who belong to the team, empty
 *   while nobody has been assigned to it yet. The manager is not one of them: they are found
 *   through [managerId]. A volunteer may belong to several teams of the same event.
 * @property checkInZone where volunteers of this team are checked in automatically, or null while
 *   the organizer has not placed it on the map.
 */
data class Team(
    val id: String = "",
    val name: String,
    val icon: String,
    val managerId: String? = null,
    val memberIds: List<String> = emptyList(),
    val volunteersNeeded: Int,
    val checkInZone: CheckInZone? = null,
    val createdAt: Instant,
)

/**
 * The circle a volunteer has to enter to be checked in to their team.
 *
 * All three values are required: a team without a zone says so with a null [Team.checkInZone]
 * rather than with a half-filled one.
 */
data class CheckInZone(val latitude: Double, val longitude: Double, val radiusMeters: Double)
