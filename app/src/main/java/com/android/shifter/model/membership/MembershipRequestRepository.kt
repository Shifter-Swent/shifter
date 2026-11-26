// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter.model.membership

/**
 * Access to the requests volunteers send to join an event. ViewModels depend on this interface,
 * never on an implementation.
 *
 * A volunteer has at most one request per event, identified by their user id.
 *
 * A Firestore-backed implementation puts the volunteer of a request in the `volunteerIds` of each
 * of its [MembershipRequest.shiftIds] when it applies or changes the shifts, and takes them out
 * when it rejects or withdraws, finding those shifts by their volunteers as well. The rules cannot
 * require both sides to be written together, so another client may leave them apart. A request
 * chooses at most 490 shifts. The in-memory fakes do not model the shifts.
 *
 * Every method throws a [MembershipRequestRepositoryException] when the operation fails.
 */
interface MembershipRequestRepository {

  /**
   * Applies to [eventId]: sends [request] as a [MembershipRequestStatus.PENDING] request, and
   * returns it with its id set to [MembershipRequest.userId].
   *
   * If the volunteer already applied to this event, that request is returned unchanged: a second
   * tap never resets an accepted or rejected request to pending.
   *
   * A Firestore-backed implementation puts the volunteer in each of [MembershipRequest.shiftIds] in
   * the same write. If one of those shifts was closed since the volunteer chose it, nothing is sent
   * and it fails with [MembershipRequestRepositoryException.ShiftClosed]; one that has started is
   * denied by the rules with [MembershipRequestRepositoryException.PermissionDenied]. More than 490
   * shifts are refused with an [IllegalArgumentException].
   */
  suspend fun apply(eventId: String, request: MembershipRequest): MembershipRequest

  /**
   * Marks the request from [userId] for [eventId] as [MembershipRequestStatus.ACCEPTED] and adds
   * the user, without duplicates, to the event participants and to the members of the team they
   * applied to ([MembershipRequest.teamId]), atomically. Fails with
   * [MembershipRequestRepositoryException.Unknown] if the request or its team does not exist.
   * Security rules may instead deny the operation with
   * [MembershipRequestRepositoryException.PermissionDenied].
   */
  suspend fun accept(eventId: String, userId: String)

  /**
   * Marks the request from [userId] for [eventId] as [MembershipRequestStatus.REJECTED] and removes
   * the user from the event participants and from the members of the team they applied to,
   * atomically, without deleting the request. A manager who is rejected leaves their team without a
   * manager in the same write. Other participants are preserved; if the participants document is
   * absent, an empty list is created. A team deleted since the request was sent is left alone, so
   * the request can still be rejected. Fails with [MembershipRequestRepositoryException.Unknown] if
   * the request does not exist. Security rules may instead deny the operation with
   * [MembershipRequestRepositoryException.PermissionDenied].
   *
   * A Firestore-backed implementation also deletes the position [userId] shared with that team, in
   * the same transaction (#305), whenever the team is still there to leave, and takes them out of
   * their shifts, skipping those that are gone. The in-memory fakes do not model it.
   */
  suspend fun reject(eventId: String, userId: String)

  /**
   * Withdraws [userId] from [eventId]: deletes their request and removes them from the event
   * participants and from their team atomically, so they can apply again later. A manager who
   * withdraws leaves their team without a manager in the same write. Withdrawing without a request
   * does nothing. Only the volunteer themself can withdraw, and only from a pending or accepted
   * request: security rules deny anyone else, and a rejected request, with
   * [MembershipRequestRepositoryException.PermissionDenied], so a rejection cannot be undone by
   * applying again.
   *
   * A Firestore-backed implementation also deletes the position [userId] shared with that team, in
   * the same transaction (#305). Only an accepted request names a team to leave: withdrawing a
   * pending one has no team, and so no position, to take out. It also takes them out of their
   * shifts, skipping those that are gone. The in-memory fakes do not model it.
   */
  suspend fun withdraw(eventId: String, userId: String)

  /**
   * Moves [userId], an accepted volunteer of [eventId], into the team [teamId], in one write: they
   * leave the team their request names, when it still exists, and the missions of that team they
   * are assigned to that are neither completed nor over, so the missions they already worked keep
   * their attendance. Their request names [teamId] from then on, so a later withdrawal leaves the
   * new team. When their request names [teamId] already, they are only put back among its members
   * if they are missing from them, e.g. after a member was removed from the team directly.
   *
   * Only the organizer can move a volunteer: security rules deny anyone else with
   * [MembershipRequestRepositoryException.PermissionDenied]. A request that is missing or not
   * accepted, a volunteer who manages their team, a [teamId] that does not exist, or a request that
   * changed team during the move are reported as [MembershipRequestRepositoryException.Unknown].
   * Trying again does not help a manager: callers do not offer the move to one, whose management is
   * taken away first. [teamId] must not be blank.
   *
   * A Firestore-backed implementation also deletes the position [userId] shared with the team they
   * leave. The in-memory fakes do not model it, nor the missions.
   */
  suspend fun moveToTeam(eventId: String, userId: String, teamId: String)

  /**
   * Replaces the chosen shifts of the request from [userId] for [eventId] with [shiftIds], and its
   * availability with [availability], the slots of those shifts joined back to back, as the caller
   * computes them for [apply]. [availability] must not be empty.
   *
   * Only the volunteer themself can change their shifts, and only while the request is
   * [MembershipRequestStatus.PENDING]: a request that is missing, accepted or rejected is refused
   * with [MembershipRequestRepositoryException.Unknown], and a chosen shift closed since the
   * volunteer chose it with [MembershipRequestRepositoryException.ShiftClosed]. Security rules deny
   * with [MembershipRequestRepositoryException.PermissionDenied] anyone else, a new shift that has
   * started, and a change once the event has ended or while the team is being deleted. More than
   * 490 [shiftIds] are refused with an [IllegalArgumentException].
   *
   * A Firestore-backed implementation moves the volunteer between the shifts in the same write,
   * skipping a shift they leave that no longer exists.
   */
  suspend fun updateShifts(
      eventId: String,
      userId: String,
      shiftIds: List<String>,
      availability: List<AvailabilitySlot>,
  )

  /**
   * Returns the request from [userId] for [eventId], or null if they have not applied. Only the
   * volunteer themself and the event organizer can read it: security rules deny anyone else with
   * [MembershipRequestRepositoryException.PermissionDenied].
   */
  suspend fun getMembershipRequest(eventId: String, userId: String): MembershipRequest?

  /**
   * Returns all requests for [eventId], or an empty list if there are none.
   *
   * With Firestore's default persistent cache, an offline query may return cached data, including
   * an empty or stale list, rather than throwing
   * [MembershipRequestRepositoryException.Unavailable].
   */
  suspend fun getMembershipRequestsByEId(eventId: String): List<MembershipRequest>

  /**
   * Returns all requests from [userId] across events, keyed by the parent event ID, or an empty map
   * if there are none. Each request's document ID remains the user ID.
   *
   * With Firestore's default persistent cache, an offline query may return cached data, including
   * an empty or stale map, rather than throwing [MembershipRequestRepositoryException.Unavailable].
   */
  suspend fun getMembershipRequestsByUId(userId: String): Map<String, MembershipRequest>
}
