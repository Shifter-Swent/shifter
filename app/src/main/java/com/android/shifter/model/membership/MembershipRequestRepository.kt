// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
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

  /** Returns the request [userId] sent to [eventId], or `null` if they have not sent one. */
  suspend fun getRequest(eventId: String, userId: String): MembershipRequest?

  /**
   * Sends [request] to [eventId] as a [MembershipRequestStatus.PENDING] request, and returns it
   * with its id set to [MembershipRequest.userId].
   *
   * If the volunteer already sent a request to this event, that one is returned unchanged: a second
   * tap never resets an accepted or rejected request to pending.
   */
  suspend fun requestToJoin(eventId: String, request: MembershipRequest): MembershipRequest
}
