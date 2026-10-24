// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.checkin

import com.swent.shifter.model.mission.FakeMissionRepository
import com.swent.shifter.model.mission.Mission
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the contract the fake shares with the backend, so the ViewModels built against it see the
 * same check-ins and the same errors as with the Firestore repository.
 */
class FakeCheckInRepositoryTest {

  private val repository = FakeCheckInRepository(currentUserId = VOLUNTEER, clock = { NOW })

  @Test
  fun checkIn_recordsTheTimeTheMethodAndWhoCheckedIn() = runTest {
    repository.checkIn(EVENT_ID, MISSION_ID, VOLUNTEER, CheckInMethod.SELF)

    assertEquals(
        listOf(CheckIn(EVENT_ID, MISSION_ID, VOLUNTEER, NOW, CheckInMethod.SELF, VOLUNTEER)),
        repository.checkIns,
    )
  }

  @Test
  fun checkIn_byAManager_recordsTheManagerAsCheckedInBy() = runTest {
    repository.currentUserId = MANAGER

    repository.checkIn(EVENT_ID, MISSION_ID, VOLUNTEER, CheckInMethod.MANAGER)

    assertEquals(MANAGER, repository.checkIns.single().checkedInBy)
    assertEquals(VOLUNTEER, repository.checkIns.single().userId)
  }

  @Test
  fun checkIn_twiceToTheSameMission_throwsAlreadyCheckedInAndKeepsTheFirst() = runTest {
    repository.checkIn(EVENT_ID, MISSION_ID, VOLUNTEER, CheckInMethod.SELF)
    repository.currentUserId = MANAGER

    assertThrowsSuspending<CheckInRepositoryException.AlreadyCheckedIn> {
      repository.checkIn(EVENT_ID, MISSION_ID, VOLUNTEER, CheckInMethod.MANAGER)
    }
    assertEquals(CheckInMethod.SELF, repository.checkIns.single().method)
  }

  @Test
  fun checkIn_concurrentlyToTheSameMission_recordsOnlyOne() = runTest {
    // 100 check-ins of the same volunteer to the same mission, started at once on several threads.
    // Each result holds either success or the exception that call threw.
    val results =
        (1..100)
            .map {
              async(Dispatchers.Default) {
                runCatching {
                  repository.checkIn(EVENT_ID, MISSION_ID, VOLUNTEER, CheckInMethod.SELF)
                }
              }
            }
            .awaitAll()

    // Exactly one call gets in,
    assertEquals(1, results.count { it.isSuccess })
    // every other one is refused as a duplicate, not with another error,
    assertTrue(
        results
            .mapNotNull { it.exceptionOrNull() }
            .all { it is CheckInRepositoryException.AlreadyCheckedIn }
    )
    // and a single check-in is stored.
    assertEquals(1, repository.checkIns.size)
  }

  @Test
  fun checkIn_toAnotherMission_isAccepted() = runTest {
    repository.checkIn(EVENT_ID, MISSION_ID, VOLUNTEER, CheckInMethod.SELF)

    repository.checkIn(EVENT_ID, OTHER_MISSION_ID, VOLUNTEER, CheckInMethod.SELF)

    assertEquals(2, repository.checkIns.size)
  }

  @Test
  fun checkIn_selfForSomeoneElse_throwsNotAllowed() = runTest {
    assertThrowsSuspending<CheckInRepositoryException.NotAllowed> {
      repository.checkIn(EVENT_ID, MISSION_ID, OTHER_VOLUNTEER, CheckInMethod.SELF)
    }
    assertEquals(emptyList<CheckIn>(), repository.checkIns)
  }

  @Test
  fun checkIn_withMissions_acceptsAnAssignedVolunteer() = runTest {
    val missions = FakeMissionRepository()
    val mission = missions.createMission(mission(VOLUNTEER))
    val repository = FakeCheckInRepository(currentUserId = VOLUNTEER, missions = missions)

    repository.checkIn(EVENT_ID, mission.id, VOLUNTEER, CheckInMethod.SELF)

    assertEquals(mission.id, repository.checkIns.single().missionId)
  }

  @Test
  fun checkIn_withMissions_throwsNotAllowedForAnUnassignedVolunteer() = runTest {
    val missions = FakeMissionRepository()
    val mission = missions.createMission(mission(OTHER_VOLUNTEER))
    val repository = FakeCheckInRepository(currentUserId = MANAGER, missions = missions)

    assertThrowsSuspending<CheckInRepositoryException.NotAllowed> {
      repository.checkIn(EVENT_ID, mission.id, VOLUNTEER, CheckInMethod.MANAGER)
    }
  }

  @Test
  fun checkIn_withMissions_throwsNotAllowedForAMissingMission() = runTest {
    val repository =
        FakeCheckInRepository(currentUserId = VOLUNTEER, missions = FakeMissionRepository())

    assertThrowsSuspending<CheckInRepositoryException.NotAllowed> {
      repository.checkIn(EVENT_ID, MISSION_ID, VOLUNTEER, CheckInMethod.SELF)
    }
  }

  @Test
  fun checkIn_withMissions_wrapsAFailureToReadTheMissionInUnknown() = runTest {
    val cause = IllegalStateException("Firestore is down")
    val repository =
        FakeCheckInRepository(
            currentUserId = VOLUNTEER,
            missions = FakeMissionRepository().apply { failure = cause },
        )

    val thrown =
        assertThrowsSuspending<CheckInRepositoryException.Unknown> {
          repository.checkIn(EVENT_ID, MISSION_ID, VOLUNTEER, CheckInMethod.SELF)
        }
    assertSame(cause, thrown.cause)
  }

  @Test
  fun checkIn_withMissions_letsACancellationThrough() = runTest {
    val repository =
        FakeCheckInRepository(
            currentUserId = VOLUNTEER,
            missions = FakeMissionRepository().apply { failure = CancellationException() },
        )

    // Not wrapped: the caller's coroutine must see it to stop.
    assertThrowsSuspending<CancellationException> {
      repository.checkIn(EVENT_ID, MISSION_ID, VOLUNTEER, CheckInMethod.SELF)
    }
  }

  @Test
  fun observeCheckIns_emitsOnlyTheMissionsCheckInsAsTheyArrive() =
      runTest(UnconfinedTestDispatcher()) {
        val emissions = mutableListOf<List<CheckIn>>()
        backgroundScope.launch {
          repository.observeCheckIns(EVENT_ID, MISSION_ID).toList(emissions)
        }

        repository.checkIn(EVENT_ID, MISSION_ID, VOLUNTEER, CheckInMethod.SELF)
        repository.add(checkIn(missionId = OTHER_MISSION_ID))
        repository.add(checkIn(eventId = OTHER_EVENT_ID))

        // Only the check-in to the mission is emitted: the other two leave its list unchanged.
        assertEquals(
            listOf(emptyList(), listOf(VOLUNTEER)),
            emissions.map { checkIns -> checkIns.map { it.userId } },
        )
      }

  @Test
  fun observeMyCheckIns_emitsOnlyTheUsersCheckInsInTheEvent() = runTest {
    repository.add(checkIn(missionId = MISSION_ID))
    repository.add(checkIn(missionId = OTHER_MISSION_ID))
    repository.add(checkIn(userId = OTHER_VOLUNTEER))
    repository.add(checkIn(eventId = OTHER_EVENT_ID))

    val mine = repository.observeMyCheckIns(EVENT_ID, VOLUNTEER).first()

    assertEquals(listOf(MISSION_ID, OTHER_MISSION_ID), mine.map { it.missionId })
  }

  @Test
  fun failure_isThrownByEveryCall() = runTest {
    val failure = CheckInRepositoryException.Unavailable()
    repository.failure = failure

    assertSame(
        failure,
        assertThrowsSuspending<CheckInRepositoryException.Unavailable> {
          repository.checkIn(EVENT_ID, MISSION_ID, VOLUNTEER, CheckInMethod.SELF)
        },
    )
    assertSame(
        failure,
        assertThrowsSuspending<CheckInRepositoryException.Unavailable> {
          repository.observeCheckIns(EVENT_ID, MISSION_ID).first()
        },
    )
    assertSame(
        failure,
        assertThrowsSuspending<CheckInRepositoryException.Unavailable> {
          repository.observeMyCheckIns(EVENT_ID, VOLUNTEER).first()
        },
    )
  }

  /** Runs [block] and returns what it threw, failing the test unless it is a [T]. */
  private suspend inline fun <reified T : Throwable> assertThrowsSuspending(
      block: suspend () -> Unit
  ): T {
    val thrown = runCatching { block() }.exceptionOrNull()
    assertTrue("Expected ${T::class.simpleName} but got $thrown", thrown is T)
    return thrown as T
  }

  private fun checkIn(
      eventId: String = EVENT_ID,
      missionId: String = MISSION_ID,
      userId: String = VOLUNTEER,
  ) = CheckIn(eventId, missionId, userId, NOW, CheckInMethod.SELF, userId)

  private fun mission(vararg assigneeIds: String) =
      Mission(
          eventId = EVENT_ID,
          title = "Bar",
          description = "Serve drinks",
          volunteersNeeded = 2,
          startAt = NOW,
          endAt = NOW.plusSeconds(3600),
          assigneeIds = assigneeIds.toList(),
          createdAt = NOW,
      )

  private companion object {
    const val EVENT_ID = "event-1"
    const val OTHER_EVENT_ID = "event-2"
    const val MISSION_ID = "mission-1"
    const val OTHER_MISSION_ID = "mission-2"
    const val VOLUNTEER = "volunteer-1"
    const val OTHER_VOLUNTEER = "volunteer-2"
    const val MANAGER = "manager-1"
    val NOW: Instant = Instant.parse("2026-10-17T18:02:00Z")
  }
}
