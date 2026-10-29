// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

/**
 * In-memory [MissionRepository] for unit tests: ids are assigned in creation order, and setting
 * [failure] makes every call throw it, to exercise error handling. Like the real repository, it
 * refuses a mission that conflicts with one already created.
 */
class FakeMissionRepository : MissionRepository {

  private val missions = mutableListOf<Mission>()

  /** Every mission created so far, in creation order. */
  val created: List<Mission>
    get() = missions.toList()

  var failure: Exception? = null

  override suspend fun createMission(mission: Mission): Mission {
    failure?.let { throw it }
    require(mission.assigneeIds.size == mission.assigneeIds.toSet().size) {
      "A mission cannot list the same assignee twice"
    }
    require(mission.assigneeIds.size <= mission.volunteersNeeded) {
      "A mission needing ${mission.volunteersNeeded} volunteers cannot have " +
          "${mission.assigneeIds.size} assignees"
    }
    missions.firstOrNull { mission.conflictsWith(it) }?.let { throw MissionOverlapException(it) }
    val stored = mission.copy(id = "mission-${missions.size + 1}")
    missions += stored
    return stored
  }

  override suspend fun getMission(eventId: String, missionId: String): Mission? {
    failure?.let { throw it }
    return missions.firstOrNull { it.eventId == eventId && it.id == missionId }
  }

  override suspend fun getMissionsByEvent(eventId: String): List<Mission> {
    failure?.let { throw it }
    return missions.filter { it.eventId == eventId }
  }

  /** Filters the stored missions, where the Firestore repository queries on the team id. */
  override suspend fun getMissionsByTeams(eventId: String, teamIds: Set<String>): List<Mission> {
    failure?.let { throw it }
    return missions.filter { it.eventId == eventId && it.teamId != null && it.teamId in teamIds }
  }

  override suspend fun getMissionsAssignedTo(userId: String): List<Mission> {
    failure?.let { throw it }
    return missions.filter { userId in it.assigneeIds }.sortedBy { it.startAt }
  }

  override suspend fun updateAssignees(
      eventId: String,
      missionId: String,
      assigneeIds: List<String>,
  ) {
    failure?.let { throw it }
    require(assigneeIds.size == assigneeIds.toSet().size) {
      "A mission cannot list the same assignee twice"
    }
    val index = missions.indexOfFirst { it.eventId == eventId && it.id == missionId }
    if (index < 0) throw MissionRepositoryException.NotFound()
    val mission = missions[index]
    require(assigneeIds.size <= mission.volunteersNeeded) {
      "A mission needing ${mission.volunteersNeeded} volunteers cannot have ${assigneeIds.size} assignees"
    }
    missions[index] = mission.copy(assigneeIds = assigneeIds)
  }
}
