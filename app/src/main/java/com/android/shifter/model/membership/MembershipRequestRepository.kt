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
   * the user to the event participants without duplicates, atomically. If the request does not
   * exist, a backend NOT_FOUND error is translated to
   * [MembershipRequestRepositoryException.Unknown]. Security rules may instead deny the operation
   * with [MembershipRequestRepositoryException.PermissionDenied].
   */
  suspend fun accept(eventId: String, userId: String)

  /**
   * Marks the request from [userId] for [eventId] as [MembershipRequestStatus.REJECTED] and removes
   * the user from the event participants atomically, without deleting the request. Other
   * participants are preserved; if the participants document is absent, an empty list is created.
   * Fails if the request does not exist: a backend NOT_FOUND error is translated to
   * [MembershipRequestRepositoryException.Unknown]. Security rules may instead deny the operation
   * with [MembershipRequestRepositoryException.PermissionDenied].
   */
  suspend fun reject(eventId: String, userId: String)

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
