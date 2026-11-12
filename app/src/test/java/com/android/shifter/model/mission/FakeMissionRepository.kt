// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update

/**
 * In-memory [MissionRepository] for unit tests: ids are assigned in creation order, and setting
 * [failure] makes every call throw it, to exercise error handling. Like the real repository, it
 * refuses a mission that conflicts with one already created.
 */
class FakeMissionRepository : MissionRepository {

  /** A flow, so that each write reaches the observers like a Firestore listener. */
  private val state = MutableStateFlow<List<Mission>>(emptyList())

  private val missions: List<Mission>
    get() = state.value

  /** Every mission created so far, in creation order. */
  val created: List<Mission>
    get() = missions

  /** A flow, so that a failure set while a flow is observed ends it, like a Firestore listener. */
  private val failures = MutableStateFlow<Exception?>(null)

  var failure: Exception?
    get() = failures.value
    set(value) {
      failures.value = value
    }

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
    state.update { it + stored }
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
    return missions.assignedTo(userId)
  }

  override fun observeMissionsAssignedTo(userId: String): Flow<List<Mission>> =
      combine(state, failures) { all, failure ->
            failure?.let { throw it }
            all.assignedTo(userId)
          }
          // A change to someone else's missions leaves the list as is: not emitted, like a
          // Firestore listener that only sees its own query.
          .distinctUntilChanged()

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
    replace(index, mission.copy(assigneeIds = assigneeIds))
  }

  override suspend fun updateMission(mission: Mission) {
    failure?.let { throw it }
    requireValidUpdate(mission)
    val index = missions.indexOfFirst { it.eventId == mission.eventId && it.id == mission.id }
    if (index < 0) throw MissionRepositoryException.NotFound()
    val stored = missions[index]
    val updated = stored.withEditableFieldsOf(mission)
    missions
        .firstOrNull { it.id != mission.id && updated.conflictsWith(it) }
        ?.let { throw MissionOverlapException(it) }
    requireRoomForAssignees(stored, mission)
    replace(index, updated)
  }

  override suspend fun markCompleted(eventId: String, missionId: String) {
    failure?.let { throw it }
    val index = missions.indexOfFirst { it.eventId == eventId && it.id == missionId }
    if (index < 0) throw MissionRepositoryException.NotFound()
    replace(index, missions[index].copy(completed = true))
  }

  /** Stores [mission] at [index], through [state] so the observers see the change. */
  private fun replace(index: Int, mission: Mission) {
    state.update { all -> all.toMutableList().also { it[index] = mission } }
  }

  private fun List<Mission>.assignedTo(userId: String) = filter {
    userId in it.assigneeIds
  }
      .sortedBy { it.startAt }
}
