// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.shift

import java.time.Instant

/**
 * A stretch of time a team has to be staffed for, stored under the event it belongs to.
 *
 * The event is not a property: it is the parent document of the shift. [teamId] points at a sibling
 * team under that same event. Who works the shift is deliberately absent: assigning volunteers is a
 * separate concept.
 *
 * @property id the backend document id, empty until the shift has been created.
 */
data class Shift(
    val id: String = "",
    val teamId: String,
    val startAt: Instant,
    val endAt: Instant,
    val createdAt: Instant,
)
