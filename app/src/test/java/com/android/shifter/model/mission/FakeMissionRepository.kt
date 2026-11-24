// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

import com.swent.shifter.model.notification.FakeNotificationInbox
import com.swent.shifter.model.notification.NotificationData
import com.swent.shifter.model.notification.NotificationKind
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update

/**
 * In-memory [MissionRepository] for unit tests: ids are assigned in creation order, and setting
 * [failure] makes every call throw it, to exercise error handling. Like the real repository, it
 * refuses a mission that conflicts with one already created, and [clock] tells it, as the real one,
 * whether a mission is past its end.
 *
 * [addAssignees] delivers its notifications to [inbox], as sent by [senderId], the signed-in user
 * of the real repository, with the title [eventTitle] gives, which the real one reads on the event.
 * Having no events and nobody signed in, it never fails with NotFound for a missing event nor with
 * PermissionDenied, as the real one may.
 */
class FakeMissionRepository(
    private val clock: Clock = Clock.systemUTC(),
    val inbox: FakeNotificationInbox = FakeNotificationInbox(),
    private val senderId: String = "organizer-1",
    private val eventTitle: (eventId: String) -> String = { "Event $it" },
) : MissionRepository {

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
    requireValidCreation(mission)
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
    requireDistinctAssignees(assigneeIds)
    val index = missions.indexOfFirst { it.eventId == eventId && it.id == missionId }
    if (index < 0) throw MissionRepositoryException.NotFound()
    val mission = missions[index]
    requireRoomInMission(mission.volunteersNeeded, assigneeIds.size)
    replace(index, mission.copy(assigneeIds = assigneeIds))
  }

  /**
   * Checks everything before changing anything, in the order of the real repository, then writes
   * the missions in one update and delivers the notifications, so a failure leaves both untouched.
   */
  override suspend fun addAssignees(eventId: String, additions: Map<String, List<String>>) {
    failure?.let { throw it }
    requireValidAdditions(additions)
    val wanted = additions.filterValues { it.isNotEmpty() }
    if (wanted.isEmpty()) return
    val stored =
        wanted.keys.map { id ->
          missions.firstOrNull { it.eventId == eventId && it.id == id }
              ?: throw MissionRepositoryException.NotFound()
        }
    val now = clock.instant()
    val changes = stored.mapNotNull { mission ->
      mission.withAdded(wanted.getValue(mission.id), now)?.let { mission to it }
    }
    val updated = changes.associate { (_, mission) -> mission.id to mission }
    // Ids are unique across events here, as each mission gets the next number.
    state.update { all -> all.map { updated[it.id] ?: it } }
    for ((userId, added) in missionsByAddedPerson(changes)) {
      inbox.deliver(
          listOf(userId),
          NotificationKind.MISSION_ASSIGNED,
          eventId,
          eventTitle(eventId),
          senderId,
          missionAssignedData(NotificationData.Value.ADDED, added),
      )
    }
  }

  override suspend fun updateMission(mission: Mission) {
    failure?.let { throw it }
    requireValidUpdate(mission)
    val index = missions.indexOfFirst { it.eventId == mission.eventId && it.id == mission.id }
    if (index < 0) throw MissionRepositoryException.NotFound()
    val stored = missions[index]
    requireNotCompleted(stored, clock.instant())
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
