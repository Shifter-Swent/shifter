// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter.model.membership

/**
 * Access to the requests volunteers send to join an event. ViewModels depend on this interface,
 * never on an implementation.
 *
 * A volunteer has at most one request per event, identified by their user id.
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
   */
  suspend fun apply(eventId: String, request: MembershipRequest): MembershipRequest

  /**
   * Marks the request from [userId] for [eventId] as [MembershipRequestStatus.ACCEPTED] and adds
   * the user to the event participants without duplicates, atomically. Fails if the request does
   * not exist.
   */
  suspend fun accept(eventId: String, userId: String)

  /**
   * Marks the request from [userId] for [eventId] as [MembershipRequestStatus.REJECTED]. Fails if
   * the request does not exist.
   */
  suspend fun reject(eventId: String, userId: String)

  /** Returns all requests for [eventId], or an empty list if there are none. */
  suspend fun getMembershipRequestsByEId(eventId: String): List<MembershipRequest>

  /** Returns all requests from [userId] across events, or an empty list if there are none. */
  suspend fun getMembershipRequestsByUId(userId: String): List<MembershipRequest>
}
