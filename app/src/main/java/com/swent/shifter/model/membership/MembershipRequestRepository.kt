// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.membership

/**
 * Read and write access to the volunteers' applications to an event, independent of any backend.
 *
 * ViewModels depend on this interface and never on an implementation, so the Firestore repository
 * can be replaced by a fake in unit tests.
 *
 * Every method takes the id of the event applied to: a [MembershipRequest] is stored below its
 * parent event and does not carry an event id of its own, so the caller has to name the event it is
 * working in.
 *
 * Only requests are returned, never the applicants' profiles: a request references its volunteer by
 * id alone, and reading that profile is the job of the user repository.
 */
interface MembershipRequestRepository {

  /**
   * Persists [request] under the event [eventId] and returns it with the id the backend assigns.
   */
  suspend fun createMembershipRequest(
      eventId: String,
      request: MembershipRequest,
  ): MembershipRequest

  /** Returns every request made to [eventId], or an empty list when it has none. */
  suspend fun getMembershipRequests(eventId: String): List<MembershipRequest>

  /**
   * Returns the requests made to [eventId] that are in [status], or an empty list when there is
   * none.
   *
   * A method of its own rather than a filter on [getMembershipRequests], so that an organizer
   * reviewing the pending applications of a large event does not download the ones they already
   * decided on.
   */
  suspend fun getMembershipRequestsByStatus(
      eventId: String,
      status: MembershipRequestStatus,
  ): List<MembershipRequest>

  /**
   * Records an organizer's decision on the request [requestId] of [eventId].
   *
   * Only the status is written, so accepting or rejecting an application cannot overwrite the
   * availability or the preferred teams the volunteer declared.
   */
  suspend fun setMembershipRequestStatus(
      eventId: String,
      requestId: String,
      status: MembershipRequestStatus,
  )
}
