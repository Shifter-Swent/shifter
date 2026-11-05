// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.checkin

import com.swent.shifter.model.mission.MissionRepository
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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

  /**
   * When set, a check-in is recorded at once as [CheckIn.pending], like a write Firestore queues
   * offline, and is only confirmed, with [checkIn] returning, once it completes. Completing it with
   * a [CheckInRepositoryException] stands for the backend refusing the check-in once online: it is
   * taken out again, as Firestore rolls the write back, and [checkIn] throws it. A [checkIn]
   * cancelled while it waits leaves its check-in to be settled all the same when this completes.
   */
  var confirmation: CompletableDeferred<Unit>? = null

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
      // A failure to read the mission is a CheckInRepositoryException too, as the contract says;
      // a cancellation is let through so the caller's coroutine still stops.
      val mission =
          try {
            missions.getMission(eventId, missionId)
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            throw CheckInRepositoryException.Unknown(e)
          }
      if (mission == null || userId !in mission.assigneeIds) {
        throw CheckInRepositoryException.NotAllowed()
      }
    }
    val gate = confirmation
    val recorded =
        CheckIn(eventId, missionId, userId, clock(), method, currentUserId, pending = gate != null)
    // Checks and inserts in one atomic update, so two concurrent calls cannot both get in.
    state.update { checkIns ->
      if (checkIns.any { it.matches(eventId, missionId) && it.userId == userId }) {
        throw CheckInRepositoryException.AlreadyCheckedIn()
      }
      checkIns + recorded
    }
    if (gate != null) {
      // Settled when the gate completes, even if this call was cancelled while waiting: Firestore
      // still sends a queued write once its caller has stopped waiting for it.
      gate.invokeOnCompletion { cause ->
        state.update { checkIns ->
          when (cause) {
            null -> checkIns.map { if (it == recorded) it.copy(pending = false) else it }
            is CheckInRepositoryException -> checkIns - recorded
            else -> checkIns
          }
        }
      }
      gate.await()
    }
  }

  override fun observeCheckIns(eventId: String, missionId: String): Flow<List<CheckIn>> = observe {
    it.matches(eventId, missionId)
  }

  override fun observeMyCheckIns(eventId: String, userId: String): Flow<List<CheckIn>> = observe {
    it.eventId == eventId && it.userId == userId
  }

  private fun observe(predicate: (CheckIn) -> Boolean): Flow<List<CheckIn>> = flow {
    failure?.let { throw it }
    // A change elsewhere in [state] leaves the filtered list as is: not emitted, like a Firestore
    // listener that only sees its own query.
    emitAll(state.map { checkIns -> checkIns.filter(predicate) }.distinctUntilChanged())
  }

  private fun CheckIn.matches(eventId: String, missionId: String) =
      this.eventId == eventId && this.missionId == missionId
}
