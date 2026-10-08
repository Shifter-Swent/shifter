// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.shift

import java.time.Instant

/**
 * A stretch of time a team has to be staffed for, stored under the event it belongs to.
 *
 * The model stays free of Firebase types so it can be used by ViewModels and tested without a
 * backend.
 *
 * @property id the backend document id, empty until the shift has been created.
 * @property eventId id of the event the shift belongs to. The event document is the parent of the
 *   shift, so the path carries this id and it is never stored as a field; it is read back from the
 *   path because a volunteer whose schedule spans several events has to know which event each of
 *   their shifts belongs to.
 * @property teamId id of the team this shift staffs, a sibling team under that same event.
 * @property assigneeIds ids of the `/users` documents of the volunteers scheduled to work this
 *   shift, empty while it is unstaffed. Belonging to the team is a separate thing from working a
 *   given slot: a volunteer of the team may be assigned to only some of its shifts.
 */
data class Shift(
    val id: String = "",
    val eventId: String,
    val teamId: String,
    val assigneeIds: List<String> = emptyList(),
    val startAt: Instant,
    val endAt: Instant,
    val createdAt: Instant,
)
