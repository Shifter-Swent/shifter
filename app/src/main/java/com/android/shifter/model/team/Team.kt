// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.team

import java.time.Instant

/**
 * A group of volunteers an organizer creates inside an event, led by a manager.
 *
 * @property id the backend document id, empty until the team has been created.
 * @property eventId id of the event the team belongs to. The event document is the parent of the
 *   team, so the path carries this id and it is never stored as a field.
 * @property description what the team does, as the organizer wrote it when creating it.
 * @property icon identifier of the icon the team is shown with.
 * @property managerId id of the `/users` document of the manager, or null while the team has none:
 *   an organizer creates a team before appointing someone to lead it.
 * @property memberIds ids of the `/users` documents of the volunteers who belong to the team, empty
 *   while nobody has been assigned to it yet. The manager is not one of them: they are found
 *   through [managerId]. A volunteer may belong to several teams of the same event.
 * @property volunteersNeeded how many volunteers the team needs in total, or null while that has
 *   not been set: an organizer creates a team before sizing its staffing need, which is configured
 *   on its own afterwards.
 * @property checkInZone where volunteers of this team are checked in automatically, or null while
 *   the organizer has not placed it on the map.
 */
data class Team(
    val id: String = "",
    val eventId: String,
    val name: String,
    val description: String,
    val icon: String,
    val managerId: String? = null,
    val memberIds: List<String> = emptyList(),
    val volunteersNeeded: Int? = null,
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

/**
 * The members of this team once [managerId] is its manager, or once it has none when null: the
 * manager is never a member, so the one appointed leaves the members and the previous one, if any,
 * goes back to them, at the end.
 */
internal fun Team.membersAfterAppointing(managerId: String?): List<String> {
  val previous = this.managerId?.takeIf { it != managerId && it !in memberIds }
  return memberIds.filter { it != managerId } + listOfNotNull(previous)
}
