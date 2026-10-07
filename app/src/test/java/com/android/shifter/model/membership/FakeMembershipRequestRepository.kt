// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.membership

/**
 * In-memory [MembershipRequestRepository] for ViewModel unit tests, keyed by event id then user id.
 * Set [failure] to make every call throw it.
 */
class FakeMembershipRequestRepository : MembershipRequestRepository {

  val requests: MutableMap<Pair<String, String>, MembershipRequest> = mutableMapOf()

  var failure: MembershipRequestRepositoryException? = null

  override suspend fun getRequest(eventId: String, userId: String): MembershipRequest? {
    failure?.let { throw it }
    return requests[eventId to userId]
  }

  override suspend fun requestToJoin(
      eventId: String,
      request: MembershipRequest,
  ): MembershipRequest {
    failure?.let { throw it }
    return requests.getOrPut(eventId to request.userId) {
      request.copy(id = request.userId, status = MembershipRequestStatus.PENDING)
    }
  }
}
