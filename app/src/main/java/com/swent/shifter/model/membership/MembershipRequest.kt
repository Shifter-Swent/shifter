// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.membership

import java.time.Instant

/**
 * A volunteer's application to take part in an event, stored under the event they applied to.
 *
 * The event is not a property: it is the parent document of the request. The volunteer and the
 * teams they picked are referenced by id alone; copying their profiles here would go stale the
 * moment they change them.
 *
 * @property id the backend document id, empty until the request has been created.
 * @property preferredTeamIds ids of the teams the volunteer would like to join, most wanted first.
 *   Only a preference, so it may be empty: a volunteer can apply without picking a team.
 * @property availability when the volunteer can work. Never empty: an organizer needs at least one
 *   declared time window before the request can be scheduled.
 */
data class MembershipRequest(
    val id: String = "",
    val userId: String,
    val preferredTeamIds: List<String> = emptyList(),
    val availability: List<AvailabilitySlot>,
    val status: MembershipRequestStatus = MembershipRequestStatus.PENDING,
    val createdAt: Instant,
) {
  init {
    require(availability.isNotEmpty()) {
      "MembershipRequest must contain at least one availability slot"
    }
  }
}

/** A stretch of time a volunteer declared themselves available for. */
data class AvailabilitySlot(val startAt: Instant, val endAt: Instant)

/** Where a [MembershipRequest] stands: an organizer accepts it or rejects it. */
enum class MembershipRequestStatus {
  PENDING,
  ACCEPTED,
  REJECTED,
}
