// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

/**
 * In-memory [MissionRepository] for unit tests: ids are assigned in creation order, and setting
 * [failure] makes every call throw it, to exercise error handling.
 */
class FakeMissionRepository : MissionRepository {

  private val missions = mutableListOf<Mission>()

  /** Every mission created so far, in creation order. */
  val created: List<Mission>
    get() = missions.toList()

  var failure: Exception? = null

  override suspend fun createMission(mission: Mission): Mission {
    failure?.let { throw it }
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
}
