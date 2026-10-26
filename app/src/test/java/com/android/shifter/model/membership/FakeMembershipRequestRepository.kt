// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.membership

import com.swent.shifter.model.team.TeamRepository

/**
 * In-memory [MembershipRequestRepository] for unit tests.
 *
 * @property failure when set, every call throws it, to simulate a backend error.
 * @param teams when set, decisions and withdrawals keep the requested team's members in step, like
 *   the backend does. Left null, tests that do not look at teams need not create them.
 */
class FakeMembershipRequestRepository(
    var failure: Exception? = null,
    private val teams: TeamRepository? = null,
) : MembershipRequestRepository {

  /** Requests keyed by event id, then by the volunteer's user id. */
  private val requests = mutableMapOf<String, MutableMap<String, MembershipRequest>>()

  /** Accepted volunteers of each event, kept in step with the requests like the backend does. */
  val participants = mutableMapOf<String, MutableSet<String>>()

  /** Stores [request] for [eventId] as is, to set up a test. */
  fun add(eventId: String, request: MembershipRequest) {
    requests.getOrPut(eventId) { mutableMapOf() }[request.userId] =
        request.copy(id = request.userId)
  }

  override suspend fun apply(eventId: String, request: MembershipRequest): MembershipRequest {
    failure?.let { throw it }
    val byUser = requests.getOrPut(eventId) { mutableMapOf() }
    return byUser.getOrPut(request.userId) {
      request.copy(id = request.userId, status = MembershipRequestStatus.PENDING)
    }
  }

  override suspend fun accept(eventId: String, userId: String) {
    val request = decide(eventId, userId, MembershipRequestStatus.ACCEPTED)
    participants.getOrPut(eventId) { mutableSetOf() } += userId
    teams?.addMember(eventId, request.teamId, userId)
  }

  override suspend fun reject(eventId: String, userId: String) {
    val request = decide(eventId, userId, MembershipRequestStatus.REJECTED)
    participants.getOrPut(eventId) { mutableSetOf() } -= userId
    leaveTeam(eventId, request.teamId, userId)
  }

  override suspend fun withdraw(eventId: String, userId: String) {
    failure?.let { throw it }
    val request = requests[eventId]?.remove(userId)
    participants[eventId]?.remove(userId)
    if (request?.status == MembershipRequestStatus.ACCEPTED) {
      leaveTeam(eventId, request.teamId, userId)
    }
  }

  /** Takes [userId] out of the team [teamId], and out of its management when they lead it. */
  private suspend fun leaveTeam(eventId: String, teamId: String, userId: String) {
    val teams = teams ?: return
    // The interface only demotes a manager back into the members, whom they then leave as well.
    if (teams.getTeam(eventId, teamId)?.managerId == userId) {
      teams.setManager(eventId, teamId, null)
    }
    teams.removeMember(eventId, teamId, userId)
  }

  override suspend fun getMembershipRequest(eventId: String, userId: String): MembershipRequest? {
    failure?.let { throw it }
    return requests[eventId]?.get(userId)
  }

  override suspend fun getMembershipRequestsByEId(eventId: String): List<MembershipRequest> {
    failure?.let { throw it }
    return requests[eventId]?.values?.toList().orEmpty()
  }

  override suspend fun getMembershipRequestsByUId(userId: String): Map<String, MembershipRequest> {
    failure?.let { throw it }
    return requests
        .mapNotNull { (eventId, byUser) -> byUser[userId]?.let { eventId to it } }
        .toMap()
  }

  /**
   * Sets the status of an existing request and returns it, failing like the backend when there is
   * none.
   */
  private fun decide(
      eventId: String,
      userId: String,
      status: MembershipRequestStatus,
  ): MembershipRequest {
    failure?.let { throw it }
    val byUser = requests[eventId]
    val request =
        byUser?.get(userId)
            ?: throw MembershipRequestRepositoryException.Unknown(
                IllegalStateException("No request from '$userId' for event '$eventId'")
            )
    return request.copy(status = status).also { byUser[userId] = it }
  }
}
