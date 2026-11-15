// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter.ui.components.nowcard

import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.time.FakeMinuteClock
import com.swent.shifter.model.time.MinuteClock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NowStatesTest {

  @Test
  fun nowStates_onCollection_emitsTheCombinedState() = runTest {
    val clock = FakeMinuteClock(START.minusSeconds(3_600))
    val received = mutableListOf<NowState>()

    collect(
        nowStates(MutableStateFlow(listOf(mission)), MutableStateFlow(emptySet()), clock),
        received,
    )

    assertEquals(listOf(NowState.Later(mission)), received)
  }

  @Test
  fun nowStates_whenTimePasses_followsTheBoundariesWithoutReloading() = runTest {
    val clock = FakeMinuteClock(START.minus(Duration.ofMinutes(46)))
    val received = mutableListOf<NowState>()
    collect(
        nowStates(MutableStateFlow(listOf(mission)), MutableStateFlow(emptySet()), clock),
        received,
    )

    clock.advance(Duration.ofMinutes(1))
    runCurrent()
    clock.advance(Duration.ofMinutes(45))
    runCurrent()
    clock.advance(Duration.ofHours(1))
    runCurrent()

    assertEquals(
        listOf(
            NowState.Later(mission),
            NowState.Soon(mission),
            NowState.Late(mission, null),
            NowState.DoneForToday,
        ),
        received,
    )
  }

  @Test
  fun nowStates_whenCheckInsArriveOrAreRolledBack_updatesPresence() = runTest {
    val checkedIn = MutableStateFlow(emptySet<String>())
    val received = mutableListOf<NowState>()
    collect(
        nowStates(MutableStateFlow(listOf(mission)), checkedIn, FakeMinuteClock(START)),
        received,
    )

    checkedIn.value = setOf(mission.id)
    runCurrent()
    checkedIn.value = emptySet()
    runCurrent()

    assertEquals(
        listOf(
            NowState.Late(mission, null),
            NowState.Ongoing(mission, null),
            NowState.Late(mission, null),
        ),
        received,
    )
  }

  @Test
  fun nowStates_afterAnEarlyCheckIn_becomesOngoingOnlyAtStart() = runTest {
    val clock = FakeMinuteClock(START.minusSeconds(600))
    val checkedIn = MutableStateFlow(emptySet<String>())
    val received = mutableListOf<NowState>()
    collect(nowStates(MutableStateFlow(listOf(mission)), checkedIn, clock), received)

    checkedIn.value = setOf(mission.id)
    runCurrent()
    assertEquals(listOf(NowState.Soon(mission)), received)
    clock.set(START)
    runCurrent()

    assertEquals(listOf(NowState.Soon(mission), NowState.Ongoing(mission, null)), received)
  }

  @Test
  fun nowStates_whenAMissionIsAssignedOrUnassigned_updatesTheSelection() = runTest {
    val missions = MutableStateFlow(emptyList<Mission>())
    val received = mutableListOf<NowState>()
    collect(nowStates(missions, MutableStateFlow(emptySet()), FakeMinuteClock(START)), received)

    missions.value = listOf(mission)
    runCurrent()
    missions.value = emptyList()
    runCurrent()

    assertEquals(
        listOf(NowState.DoneForToday, NowState.Late(mission, null), NowState.DoneForToday),
        received,
    )
  }

  @Test
  fun nowStates_whenMissionDetailsChange_emitsTheUpdatedMission() = runTest {
    val missions = MutableStateFlow(listOf(mission))
    val received = mutableListOf<NowState>()
    collect(
        nowStates(missions, MutableStateFlow(setOf(mission.id)), FakeMinuteClock(START)),
        received,
    )
    val renamed = mission.copy(title = "New briefing")

    missions.value = listOf(renamed)
    runCurrent()

    assertEquals(listOf(NowState.Ongoing(mission, null), NowState.Ongoing(renamed, null)), received)
  }

  @Test
  fun nowStates_whenAMissionIsClosedByHand_selectsTheNextOne() = runTest {
    val next =
        mission.copy(
            id = "next",
            startAt = START.plusSeconds(1_800),
            endAt = START.plusSeconds(5_400),
        )
    val missions = MutableStateFlow(listOf(mission, next))
    val received = mutableListOf<NowState>()
    collect(
        nowStates(missions, MutableStateFlow(setOf(mission.id)), FakeMinuteClock(START)),
        received,
    )

    missions.value = listOf(mission.copy(completed = true), next)
    runCurrent()

    assertEquals(listOf(NowState.Ongoing(mission, next), NowState.Soon(next)), received)
  }

  @Test
  fun nowStates_whenTicksKeepTheSameState_doesNotEmitDuplicates() = runTest {
    val clock = FakeMinuteClock(START)
    val received = mutableListOf<NowState>()
    collect(
        nowStates(MutableStateFlow(listOf(mission)), MutableStateFlow(emptySet()), clock),
        received,
    )

    repeat(3) {
      clock.advance(Duration.ofMinutes(1))
      runCurrent()
    }

    assertEquals(listOf(NowState.Late(mission, null)), received)
  }

  @Test
  fun nowStates_usesTheEmittedTickWithoutAnotherTimeRead() = runTest {
    val fakeClock = FakeMinuteClock(START)
    val clock =
        object : MinuteClock {
          override fun now(): Instant = error("Use the emitted instant consistently")

          override val ticks: Flow<Instant> = fakeClock.ticks
        }
    val received = mutableListOf<NowState>()

    collect(
        nowStates(MutableStateFlow(listOf(mission)), MutableStateFlow(emptySet()), clock),
        received,
    )

    assertEquals(listOf(NowState.Late(mission, null)), received)
  }

  @Test
  fun nowStates_beforeAllInputsAreKnown_emitsNothing() = runTest {
    val missions = MutableSharedFlow<List<Mission>>(replay = 1)
    val checkedIn = MutableSharedFlow<Set<String>>(replay = 1)
    val received = mutableListOf<NowState>()
    collect(nowStates(missions, checkedIn, FakeMinuteClock(START)), received)
    assertTrue(received.isEmpty())

    missions.emit(listOf(mission))
    runCurrent()
    assertTrue(received.isEmpty())
    checkedIn.emit(setOf(mission.id))
    runCurrent()

    assertEquals(listOf(NowState.Ongoing(mission, null)), received)
  }

  @Test
  fun nowStates_whenCollectionStops_releasesItsClockSubscription() = runTest {
    val clock = FakeMinuteClock(START)
    val flow = nowStates(MutableStateFlow(listOf(mission)), MutableStateFlow(emptySet()), clock)
    assertEquals(0, clock.subscriptionCount.value)
    val observer = collect(flow, mutableListOf())
    assertEquals(1, clock.subscriptionCount.value)

    observer.cancel()
    runCurrent()

    assertEquals(0, clock.subscriptionCount.value)
  }

  private fun TestScope.collect(flow: Flow<NowState>, received: MutableList<NowState>): Job {
    val job = backgroundScope.launch { flow.collect { received += it } }
    runCurrent()
    return job
  }

  private val mission =
      Mission(
          id = "mission",
          eventId = "event",
          title = "Welcome desk",
          description = "Briefing",
          volunteersNeeded = 1,
          startAt = START,
          endAt = START.plusSeconds(3_600),
          createdAt = START.minusSeconds(86_400),
      )

  private companion object {
    val START: Instant = Instant.parse("2026-11-14T12:00:00Z")
  }
}
