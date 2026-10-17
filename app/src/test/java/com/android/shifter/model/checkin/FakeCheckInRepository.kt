// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.checkin

import com.swent.shifter.model.mission.MissionRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [CheckInRepository] for unit tests. Its flows are live: a check-in recorded while a
 * test collects them is emitted right away.
 *
 * Like the backend, it refuses a second check-in to the same mission, and a [CheckInMethod.SELF]
 * check-in of someone other than [currentUserId]. It does not know who manages which team, so any
 * [CheckInMethod.MANAGER] check-in by [currentUserId] is accepted.
 *
 * @property currentUserId the signed-in user, recorded as [CheckIn.checkedInBy].
 * @property failure when set, every call and every new collection throws it, to simulate a backend
 *   error.
 * @param missions when set, only a volunteer in the mission's assignees can be checked in, like the
 *   backend does. Left null, tests that do not look at assignments need not create missions.
 * @param clock gives [CheckIn.checkedInAt], as the backend's clock would.
 */
class FakeCheckInRepository(
    var currentUserId: String = "user-1",
    var failure: Exception? = null,
    private val missions: MissionRepository? = null,
    private val clock: () -> Instant = { Instant.EPOCH },
) : CheckInRepository {

  private val state = MutableStateFlow<List<CheckIn>>(emptyList())

  /** Every check-in recorded so far, in recording order. */
  val checkIns: List<CheckIn>
    get() = state.value

  /** Stores [checkIn] as is, without checking the rules, to set up a test. */
  fun add(checkIn: CheckIn) {
    state.update { it + checkIn }
  }

  override suspend fun checkIn(
      eventId: String,
      missionId: String,
      userId: String,
      method: CheckInMethod,
  ) {
    failure?.let { throw it }
    if (method == CheckInMethod.SELF && userId != currentUserId) {
      throw CheckInRepositoryException.NotAllowed()
    }
    if (missions != null) {
      val mission = missions.getMission(eventId, missionId)
      if (mission == null || userId !in mission.assigneeIds) {
        throw CheckInRepositoryException.NotAllowed()
      }
    }
    if (state.value.any { it.matches(eventId, missionId) && it.userId == userId }) {
      throw CheckInRepositoryException.AlreadyCheckedIn()
    }
    add(CheckIn(eventId, missionId, userId, clock(), method, checkedInBy = currentUserId))
  }

  override fun observeCheckIns(eventId: String, missionId: String): Flow<List<CheckIn>> = observe {
    it.matches(eventId, missionId)
  }

  override fun observeMyCheckIns(eventId: String, userId: String): Flow<List<CheckIn>> = observe {
    it.eventId == eventId && it.userId == userId
  }

  private fun observe(predicate: (CheckIn) -> Boolean): Flow<List<CheckIn>> = flow {
    failure?.let { throw it }
    emitAll(state.map { checkIns -> checkIns.filter(predicate) })
  }

  private fun CheckIn.matches(eventId: String, missionId: String) =
      this.eventId == eventId && this.missionId == missionId
}
