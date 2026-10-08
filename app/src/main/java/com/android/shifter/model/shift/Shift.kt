// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.shift

import java.time.Instant

/**
 * A stretch of time a team has to be staffed for, stored under the event it belongs to.
 *
 * The event is not a property: it is the parent document of the shift. [teamId] points at a sibling
 * team under that same event.
 *
 * @property id the backend document id, empty until the shift has been created.
 * @property assigneeIds ids of the `/users` documents of the volunteers scheduled to work this
 *   shift, empty while it is unstaffed. Belonging to the team is a separate thing from working a
 *   given slot: a volunteer of the team may be assigned to only some of its shifts.
 */
data class Shift(
    val id: String = "",
    val teamId: String,
    val assigneeIds: List<String> = emptyList(),
    val startAt: Instant,
    val endAt: Instant,
    val createdAt: Instant,
)
