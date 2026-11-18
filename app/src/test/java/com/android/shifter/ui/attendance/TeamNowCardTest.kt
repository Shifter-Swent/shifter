// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.attendance

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TeamNowCardTest {

  @get:Rule val compose = createComposeRule()

  private val now = Instant.parse("2027-06-20T14:10:00Z")
  private val utc = ZoneId.of("UTC")

  private val chloe = AttendanceVolunteer("u1", "Chloé Dubois")
  private val daniel = AttendanceVolunteer("u2", "Daniel Meier")
  private val noah = AttendanceVolunteer("u3", "Noah Favre")

  /** A mission that started ten minutes ago and runs until 15:30. */
  private val sorting = mission("m1", "Food sorting")
  private val welcome = mission("m2", "Welcome desk")

  /** The next mission, its check-in open: two of the three it needs are there, one is not. */
  private val briefing =
      mission(
          "m3",
          "Briefing",
          startAt = now + Duration.ofMinutes(10),
          needed = 3,
          assignees = listOf(chloe.copy(checkedInAt = now), daniel.copy(checkedInAt = now), noah),
      )

  /** The one after it, whose check-in nobody can have used yet. */
  private val debrief = mission("m4", "Debrief", startAt = now + Duration.ofMinutes(25), needed = 3)

  private fun mission(
      id: String,
      title: String,
      startAt: Instant = Instant.parse("2027-06-20T14:00:00Z"),
      needed: Int = 2,
      assignees: List<AttendanceVolunteer> = emptyList(),
  ) =
      AttendanceMission(
          id = id,
          title = title,
          teamId = "team",
          teamName = "Logistics",
          volunteersNeeded = needed,
          startAt = startAt,
          endAt = Instant.parse("2027-06-20T15:30:00Z"),
          assignees = assignees,
          attendanceKnown = true,
      )

  private fun show(
      state: TeamNowState,
      onCheckIn: (String, String) -> Unit = { _, _ -> },
      onViewOnMap: () -> Unit = {},
  ) {
    show(mutableStateOf(state), onCheckIn, onViewOnMap)
  }

  /** The state the view model reports once it has checked that row in: it is no longer missing. */
  private fun TeamNowState.Missing.without(missionId: String, userId: String) =
      copy(
          missing =
              missing.filterNot { it.mission.id == missionId && it.volunteer.userId == userId }
      )

  /** The card on a state the test can refresh, as the view model does once a check-in lands. */
  private fun show(
      state: MutableState<out TeamNowState>,
      onCheckIn: (String, String) -> Unit = { _, _ -> },
      onViewOnMap: () -> Unit = {},
  ) {
    compose.setContent {
      ShifterTheme {
        TeamNowCard(
            state = state.value,
            now = now,
            onCheckIn = onCheckIn,
            onViewOnMap = onViewOnMap,
            zone = utc,
        )
      }
    }
  }

  @Test
  fun missing_showsTheCardWithWhoIsMissingAndHowLate() {
    show(TeamNowState.Missing(listOf(MissingAssignee(sorting, chloe)), onDuty = 3))

    compose.onNodeWithTag(TeamNowCardTestTags.CARD).assertIsDisplayed()
    compose.onNodeWithTag(TeamNowCardTestTags.STATUS).assertIsDisplayed()
    compose.onNodeWithText("Needs you").assertIsDisplayed()
    compose.onNodeWithText("1 volunteer missing").assertIsDisplayed()
    compose.onNodeWithTag(TeamNowCardTestTags.missing("m1", "u1")).assertIsDisplayed()
    compose.onNodeWithText("Chloé Dubois").assertIsDisplayed()
    compose.onNodeWithText("Food sorting · late 10 min").assertIsDisplayed()
    compose.onNodeWithText("Check in").assertIsDisplayed()
    compose.onNodeWithText("3 others checked in").assertIsDisplayed()
  }

  @Test
  fun missing_countsPeopleAndMissionsOnce() {
    // Three rows, two people, two missions: a volunteer missing from two missions is one person
    // to find and is listed under each, and a mission missing two people still has one bar.
    show(
        TeamNowState.Missing(
            listOf(
                MissingAssignee(sorting, chloe),
                MissingAssignee(welcome, chloe),
                MissingAssignee(sorting, daniel),
            ),
            onDuty = 1,
        )
    )

    compose.onNodeWithText("2 volunteers missing").assertIsDisplayed()
    compose.onNodeWithTag(TeamNowCardTestTags.missing("m1", "u1")).assertIsDisplayed()
    compose.onNodeWithTag(TeamNowCardTestTags.missing("m2", "u1")).assertIsDisplayed()
    compose.onNodeWithTag(TeamNowCardTestTags.missing("m1", "u2")).assertIsDisplayed()
    compose.onAllNodesWithTag(TeamNowCardTestTags.TIME_BAR).assertCountEquals(2)
  }

  @Test
  fun checkIn_callsBackWithTheMissionAndTheVolunteer() {
    var checkedIn: Pair<String, String>? = null
    show(
        TeamNowState.Missing(listOf(MissingAssignee(sorting, chloe)), onDuty = 3),
        onCheckIn = { missionId, userId -> checkedIn = missionId to userId },
    )

    compose.onNodeWithTag(TeamNowCardTestTags.checkIn("m1", "u1")).performClick()

    assertEquals("m1" to "u1", checkedIn)
  }

  @Test
  fun checkIn_whenTheStateDropsThem_keepsTheVolunteerInGreenForAMoment() {
    // The green says the check-in happened, which is the state dropping the volunteer, as the
    // view model does in the frame of the tap. Their row is then held a moment where it was,
    // and the headline goes on counting what the state reports rather than what the card shows.
    val state =
        mutableStateOf(
            TeamNowState.Missing(
                listOf(MissingAssignee(sorting, chloe), MissingAssignee(sorting, daniel)),
                onDuty = 3,
            )
        )
    show(state)
    compose.mainClock.autoAdvance = false

    compose.onNodeWithTag(TeamNowCardTestTags.checkIn("m1", "u1")).performClick()
    state.value = state.value.without("m1", "u1")
    Snapshot.sendApplyNotifications()
    compose.mainClock.advanceTimeByFrame()

    compose.onNodeWithTag(TeamNowCardTestTags.justCheckedIn("m1", "u1")).assertExists()
    compose.onNodeWithText("Checked in").assertIsDisplayed()
    compose.onNodeWithTag(TeamNowCardTestTags.missing("m1", "u1")).assertDoesNotExist()
    // Checked in, their row can no longer be tapped: the check-in cannot be made twice.
    compose.onNodeWithTag(TeamNowCardTestTags.checkIn("m1", "u1")).assertDoesNotExist()
    compose.onNodeWithText("1 volunteer missing").assertExists()
    compose.onNodeWithText("2 volunteers missing").assertDoesNotExist()

    compose.mainClock.advanceTimeBy(JUST_CHECKED_IN_MILLIS + 1_000)

    // The moment over, nothing is left of their row; the one the state still reports keeps its.
    compose.onNodeWithTag(TeamNowCardTestTags.justCheckedIn("m1", "u1")).assertDoesNotExist()
    compose.onNodeWithText("Checked in").assertDoesNotExist()
    compose.onNodeWithTag(TeamNowCardTestTags.missing("m1", "u1")).assertDoesNotExist()
    compose.onNodeWithTag(TeamNowCardTestTags.missing("m1", "u2")).assertExists()
  }

  @Test
  fun checkIn_theStateDoesNotTakeUp_isNotConfirmedInGreen() {
    // The view model refused it -- outside the check-in window, or the mission closed -- so it
    // goes on reporting the volunteer missing. A row turned green here would say someone is
    // there who is not, so the row stays the missing row it is, and can be tried again.
    var taps = 0
    show(
        TeamNowState.Missing(
            listOf(MissingAssignee(sorting, chloe), MissingAssignee(sorting, daniel)),
            onDuty = 3,
        ),
        onCheckIn = { _, _ -> taps++ },
    )
    compose.mainClock.autoAdvance = false

    compose.onNodeWithTag(TeamNowCardTestTags.checkIn("m1", "u1")).performClick()
    compose.mainClock.advanceTimeByFrame()

    assertEquals(1, taps)
    compose.onNodeWithTag(TeamNowCardTestTags.justCheckedIn("m1", "u1")).assertDoesNotExist()
    compose.onNodeWithText("Checked in").assertDoesNotExist()
    compose.onNodeWithTag(TeamNowCardTestTags.missing("m1", "u1")).assertIsDisplayed()
    compose.onNodeWithTag(TeamNowCardTestTags.checkIn("m1", "u1")).assertExists()
    compose.onNodeWithText("2 volunteers missing").assertExists()
  }

  @Test
  fun checkIn_undoneWithinTheMoment_putsTheRowBackAmongTheMissing() {
    // Undo before the five seconds are out: the view model reports the volunteer missing again,
    // and the card follows it rather than go on confirming a check-in that was taken back.
    val full =
        TeamNowState.Missing(
            listOf(MissingAssignee(sorting, chloe), MissingAssignee(sorting, daniel)),
            onDuty = 3,
        )
    val state = mutableStateOf(full)
    show(state)
    compose.mainClock.autoAdvance = false

    compose.onNodeWithTag(TeamNowCardTestTags.checkIn("m1", "u1")).performClick()
    state.value = full.without("m1", "u1")
    Snapshot.sendApplyNotifications()
    compose.mainClock.advanceTimeByFrame()

    compose.onNodeWithTag(TeamNowCardTestTags.justCheckedIn("m1", "u1")).assertExists()

    state.value = full
    Snapshot.sendApplyNotifications()
    compose.mainClock.advanceTimeByFrame()

    compose.onNodeWithTag(TeamNowCardTestTags.justCheckedIn("m1", "u1")).assertDoesNotExist()
    compose.onNodeWithText("Checked in").assertDoesNotExist()
    compose.onNodeWithTag(TeamNowCardTestTags.missing("m1", "u1")).assertIsDisplayed()
    // And she can be checked in again, the undo having left nothing behind.
    compose.onNodeWithTag(TeamNowCardTestTags.checkIn("m1", "u1")).assertExists()
  }

  @Test
  fun checkIn_tappedAgainAfterARefusal_triesAgainAndKeepsOneRow() {
    // A refused check-in leaves the row tappable, so the same row can be tapped twice. Each tap
    // is forwarded, the view model answering for it, and the two make one row, kept until a
    // moment after the second tap rather than taken away by the timer of the first.
    val state =
        mutableStateOf(
            TeamNowState.Missing(
                listOf(MissingAssignee(sorting, chloe), MissingAssignee(sorting, daniel)),
                onDuty = 3,
            )
        )
    var taps = 0
    show(state, onCheckIn = { _, _ -> taps++ })
    compose.mainClock.autoAdvance = false

    compose.onNodeWithTag(TeamNowCardTestTags.checkIn("m1", "u1")).performClick()
    compose.mainClock.advanceTimeBy(2_000)
    compose.onNodeWithTag(TeamNowCardTestTags.checkIn("m1", "u1")).performClick()
    state.value = state.value.without("m1", "u1")
    Snapshot.sendApplyNotifications()
    compose.mainClock.advanceTimeByFrame()

    assertEquals(2, taps)
    compose.onAllNodesWithTag(TeamNowCardTestTags.justCheckedIn("m1", "u1")).assertCountEquals(1)

    // The first tap's five seconds are over by now; the moment belongs to the second tap.
    compose.mainClock.advanceTimeBy(JUST_CHECKED_IN_MILLIS - 1_000)

    compose.onNodeWithTag(TeamNowCardTestTags.justCheckedIn("m1", "u1")).assertExists()

    compose.mainClock.advanceTimeBy(2_000)

    compose.onNodeWithTag(TeamNowCardTestTags.justCheckedIn("m1", "u1")).assertDoesNotExist()
  }

  @Test
  fun missing_withUnknownAttendance_saysSoRatherThanNobodyThere() {
    // Not being seen is not being absent: the manager is told, not shown an empty team.
    show(
        TeamNowState.Missing(
            listOf(MissingAssignee(sorting, chloe)),
            onDuty = 0,
            attendanceUnknown = listOf(welcome),
        )
    )

    compose.onNodeWithText("Attendance not known for 1 mission").assertIsDisplayed()
    // And nobody else is reported at work: "0 others checked in" would read as an empty team.
    compose.onNodeWithText("0 others checked in").assertDoesNotExist()
  }

  @Test
  fun checkIn_keepsTheMissionsTimeBarWhileTheGreenRowIsThere() {
    // The last volunteer missing from a mission is checked in: a green row with no bar over it
    // would read as a mission that has ended, so the bar stays as long as the row does. The
    // other mission, untouched, keeps its own all along.
    val state =
        mutableStateOf(
            TeamNowState.Missing(
                listOf(MissingAssignee(sorting, chloe), MissingAssignee(welcome, daniel)),
                onDuty = 1,
            )
        )
    show(state)
    compose.mainClock.autoAdvance = false

    compose.onNodeWithTag(TeamNowCardTestTags.checkIn("m1", "u1")).performClick()
    state.value = state.value.without("m1", "u1")
    Snapshot.sendApplyNotifications()
    compose.mainClock.advanceTimeByFrame()

    compose.onNodeWithTag(TeamNowCardTestTags.justCheckedIn("m1", "u1")).assertExists()
    compose.onAllNodesWithTag(TeamNowCardTestTags.TIME_BAR).assertCountEquals(2)
    compose.onNodeWithText("Food sorting").assertIsDisplayed()

    compose.mainClock.advanceTimeBy(JUST_CHECKED_IN_MILLIS + 1_000)

    // The row gone, so is its bar; the mission still missing someone keeps its own.
    compose.onAllNodesWithTag(TeamNowCardTestTags.TIME_BAR).assertCountEquals(1)
    compose.onNodeWithText("Food sorting").assertDoesNotExist()
    compose.onNodeWithText("Welcome desk").assertIsDisplayed()
  }

  @Test
  fun checkIn_withSomeoneStillMissingFromTheMission_keepsOneTimeBar() {
    // A green row and a missing row of the same mission are two rows of one mission: one bar.
    val state =
        mutableStateOf(
            TeamNowState.Missing(
                listOf(MissingAssignee(sorting, chloe), MissingAssignee(sorting, noah)),
                onDuty = 0,
            )
        )
    show(state)
    compose.mainClock.autoAdvance = false

    compose.onNodeWithTag(TeamNowCardTestTags.checkIn("m1", "u1")).performClick()
    state.value = state.value.without("m1", "u1")
    Snapshot.sendApplyNotifications()
    compose.mainClock.advanceTimeByFrame()

    compose.onNodeWithTag(TeamNowCardTestTags.justCheckedIn("m1", "u1")).assertExists()
    compose.onNodeWithTag(TeamNowCardTestTags.missing("m1", "u3")).assertExists()
    compose.onAllNodesWithTag(TeamNowCardTestTags.TIME_BAR).assertCountEquals(1)
  }

  @Test
  fun viewOnMap_isTheCardsOnlyAction() {
    var opened = false
    show(
        TeamNowState.Missing(listOf(MissingAssignee(sorting, chloe)), onDuty = 3),
        onViewOnMap = { opened = true },
    )

    compose.onAllNodesWithTag(TeamNowCardTestTags.ACTION).assertCountEquals(1)
    compose.onNodeWithText("View team on map").assertIsDisplayed()
    compose.onNodeWithTag(TeamNowCardTestTags.ACTION).performClick()

    assertEquals(true, opened)
  }

  @Test
  fun missing_withANameTheProfileCannotGive_readsAStandIn() {
    // An id is not a name: a volunteer whose profile could not be read is still a row to tap,
    // under the stand-in they are given wherever they are listed. What their avatar draws is
    // AvatarInitialsTest's: the card draws it without speaking it.
    val unnamed = AttendanceVolunteer("u8", null)
    show(TeamNowState.Missing(listOf(MissingAssignee(sorting, unnamed)), onDuty = 0))

    compose.onNodeWithText("Unnamed volunteer").assertIsDisplayed()
    compose.onNodeWithTag(TeamNowCardTestTags.missing("m1", "u8")).assertIsDisplayed()
  }

  @Test
  fun missing_leavesTheDecorativeInitialOutOfTheSemantics() {
    // The avatar repeats the first letter of the name next to it, so it is drawn and not spoken:
    // a reader that read both would say "C, Chloe Dubois".
    show(TeamNowState.Missing(listOf(MissingAssignee(sorting, chloe)), onDuty = 3))

    // The row as a reader reads it, the avatar being drawn under cleared semantics: the merged
    // row no longer carries the lone initial, which it did when the avatar spoke for itself.
    compose.onNodeWithText("C").assertDoesNotExist()
    // What the row is for is still read: who it is, how late they are, and what a tap does.
    compose.onNodeWithText("Chloé Dubois").assertIsDisplayed()
    compose.onNodeWithText("Check in").assertIsDisplayed()
  }

  @Test
  fun checkIn_withAVolunteerMissingFromTwoMissions_takesOneMissionAtATime() {
    val missions = mutableListOf<String>()
    val state =
        mutableStateOf(
            TeamNowState.Missing(
                listOf(MissingAssignee(sorting, chloe), MissingAssignee(welcome, chloe)),
                onDuty = 3,
            )
        )
    show(state, onCheckIn = { missionId, _ -> missions += missionId })
    compose.mainClock.autoAdvance = false

    compose.onNodeWithTag(TeamNowCardTestTags.checkIn("m1", "u1")).performClick()
    state.value = state.value.without("m1", "u1")
    Snapshot.sendApplyNotifications()
    compose.mainClock.advanceTimeByFrame()

    compose.onNodeWithTag(TeamNowCardTestTags.justCheckedIn("m1", "u1")).assertExists()
    // She is still missing from the other mission, and is still checked in to it on its own.
    compose.onNodeWithTag(TeamNowCardTestTags.missing("m2", "u1")).assertExists()
    compose.onNodeWithTag(TeamNowCardTestTags.justCheckedIn("m2", "u1")).assertDoesNotExist()

    compose.onNodeWithTag(TeamNowCardTestTags.checkIn("m2", "u1")).performClick()
    state.value = state.value.without("m2", "u1")
    Snapshot.sendApplyNotifications()
    compose.mainClock.advanceTimeByFrame()

    assertEquals(listOf("m1", "m2"), missions)
    compose.onNodeWithTag(TeamNowCardTestTags.justCheckedIn("m2", "u1")).assertExists()
  }

  @Test
  fun checkIn_onOneOfTwoMissions_leavesTheOthersCountAlone() {
    // Chloe stays missing from the other mission, so the state does not count her present: the
    // card must not take her off the others either.
    val state =
        mutableStateOf(
            TeamNowState.Missing(
                listOf(MissingAssignee(sorting, chloe), MissingAssignee(welcome, chloe)),
                onDuty = 3,
            )
        )
    show(state)
    compose.mainClock.autoAdvance = false

    compose.onNodeWithTag(TeamNowCardTestTags.checkIn("m1", "u1")).performClick()
    // The view model drops the row it wrote, and still reports her missing from the other one.
    state.value = state.value.without("m1", "u1")
    Snapshot.sendApplyNotifications()
    compose.mainClock.advanceTimeByFrame()

    compose.onNodeWithText("3 others checked in").assertExists()
  }

  @Test
  fun checkIn_whenTheStateCountsThemPresent_keepsTheOthersSteady() {
    val state =
        mutableStateOf(
            TeamNowState.Missing(
                listOf(MissingAssignee(sorting, chloe), MissingAssignee(sorting, daniel)),
                onDuty = 3,
            )
        )
    show(state)
    compose.mainClock.autoAdvance = false

    compose.onNodeWithTag(TeamNowCardTestTags.checkIn("m1", "u1")).performClick()
    // Chloe is now present: the state stops reporting her missing and counts her on duty.
    state.value = TeamNowState.Missing(listOf(MissingAssignee(sorting, daniel)), onDuty = 4)
    Snapshot.sendApplyNotifications()
    compose.mainClock.advanceTimeByFrame()

    // Her green row is one of the rows, so she is not also one of the others: 3, not 4.
    compose.onNodeWithTag(TeamNowCardTestTags.justCheckedIn("m1", "u1")).assertExists()
    compose.onNodeWithText("3 others checked in").assertExists()
  }

  @Test
  fun running_showsHowManyAreAtWorkAndABarPerMission() {
    var opened = false
    // Both missions have the two volunteers they need, so the team is all on duty.
    val present = listOf(chloe.copy(checkedInAt = now), daniel.copy(checkedInAt = now))
    show(
        TeamNowState.Running(
            listOf(sorting.copy(assignees = present), welcome.copy(assignees = present)),
            onDuty = 2,
            next = emptyList(),
        ),
        onViewOnMap = { opened = true },
    )

    compose.onNodeWithText("All on duty").assertIsDisplayed()
    compose.onNodeWithText("2 at work").assertIsDisplayed()
    compose.onAllNodesWithTag(TeamNowCardTestTags.TIME_BAR).assertCountEquals(2)
    compose.onNodeWithText("Food sorting").assertIsDisplayed()
    compose.onNodeWithText("Welcome desk").assertIsDisplayed()
    // Nobody is missing, so there is no row to tap, and nothing to come, so no strip.
    compose.onNodeWithText("Check in").assertDoesNotExist()
    compose.onNodeWithTag(TeamNowCardTestTags.NEXT).assertDoesNotExist()
    // The one action, here as while someone is missing.
    compose.onAllNodesWithTag(TeamNowCardTestTags.ACTION).assertCountEquals(1)
    compose.onNodeWithText("View team on map").assertIsDisplayed()
    compose.onNodeWithTag(TeamNowCardTestTags.ACTION).performClick()

    assertEquals(true, opened)
  }

  @Test
  fun running_withANextMission_saysWhoArrivedAndWhatItIsShortOf() {
    show(TeamNowState.Running(listOf(sorting), onDuty = 4, next = listOf(briefing, debrief)))

    // The next one only: the manager is told what is coming, not the whole day.
    compose.onAllNodesWithTag(TeamNowCardTestTags.NEXT).assertCountEquals(1)
    compose.onNodeWithText("Then, in 10 min").assertIsDisplayed()
    compose.onNodeWithText("Briefing · 2 of 3 arrived").assertIsDisplayed()
    compose.onNodeWithText("Short of 1 volunteer").assertIsDisplayed()
  }

  @Test
  fun running_withTheNextCheckInNotOpen_saysHowManyAreExpected() {
    // Nobody can have checked in yet, so "0 of 3 arrived" would read as a team that has not come.
    show(TeamNowState.Running(listOf(sorting), onDuty = 4, next = listOf(debrief)))

    compose.onNodeWithText("Then, in 25 min").assertIsDisplayed()
    compose.onNodeWithText("Debrief · 3 volunteers expected").assertIsDisplayed()
    compose.onNodeWithText("0 of 3 arrived").assertDoesNotExist()
    compose.onNodeWithText("Short of 3 volunteers").assertDoesNotExist()
  }

  @Test
  fun running_withNoAttendanceKnown_countsTheMissionsRatherThanNobodyAtWork() {
    // The first load: not being seen is not being absent, and nobody seen is not an empty team.
    show(
        TeamNowState.Running(
            listOf(sorting, welcome),
            onDuty = 0,
            next = emptyList(),
            attendanceUnknown = listOf(sorting, welcome),
        )
    )

    compose.onNodeWithText("2 missions running").assertIsDisplayed()
    compose.onNodeWithText("Attendance not known for 2 missions").assertIsDisplayed()
    compose.onNodeWithText("0 at work").assertDoesNotExist()
    // Nor is the team all on duty: nobody has been seen, which is not everybody being there.
    compose.onNodeWithText("All on duty").assertDoesNotExist()
  }

  @Test
  fun running_withNobodyAssigned_doesNotReportATeamAtWork() {
    // Nobody is assigned to the mission, and its attendance is known: there is nobody to be
    // missing, so the team is Running with nobody on duty. "All on duty" would speak for a team
    // that is not there, and "0 at work" would read as one that has not come.
    show(TeamNowState.Running(listOf(sorting), onDuty = 0, next = emptyList()))

    compose.onNodeWithText("Now").assertIsDisplayed()
    compose.onNodeWithText("1 mission running").assertIsDisplayed()
    compose.onNodeWithText("All on duty").assertDoesNotExist()
    compose.onNodeWithText("0 at work").assertDoesNotExist()
  }

  @Test
  fun running_withAMissionShortOfVolunteers_doesNotSayAllOnDuty() {
    // Both of the mission's assignees are there, so nobody is missing, but it asked for three:
    // "All on duty" would read as a mission with the people it needs. The count still holds.
    val short =
        mission(
            "m5",
            "Gate",
            needed = 3,
            assignees = listOf(chloe.copy(checkedInAt = now), daniel.copy(checkedInAt = now)),
        )
    show(TeamNowState.Running(listOf(short), onDuty = 2, next = emptyList()))

    compose.onNodeWithText("Now").assertIsDisplayed()
    compose.onNodeWithText("2 at work").assertIsDisplayed()
    compose.onNodeWithText("All on duty").assertDoesNotExist()
  }

  @Test
  fun running_withOneMissionsAttendanceUnknown_countsOnlyWhatIsKnown() {
    // One of the two is followed, the other is not: the card counts the people it has been
    // shown, says so for the mission it has not, and does not speak for the whole team.
    val unknown = welcome.copy(attendanceKnown = false)
    show(
        TeamNowState.Running(
            listOf(sorting, unknown),
            onDuty = 2,
            next = emptyList(),
            attendanceUnknown = listOf(unknown),
        )
    )

    compose.onNodeWithText("Now").assertIsDisplayed()
    compose.onNodeWithText("2 at work").assertIsDisplayed()
    compose.onNodeWithText("Attendance not known for 1 mission").assertIsDisplayed()
    compose.onNodeWithText("All on duty").assertDoesNotExist()
  }

  @Test
  fun running_withTheNextAttendanceWaiting_saysHowManyAreExpected() {
    // Its check-in is open, but its attendance has not arrived: who is there is not known yet,
    // so the strip says how many are expected rather than counting the two it was given.
    show(
        TeamNowState.Running(
            listOf(sorting),
            onDuty = 4,
            next = listOf(briefing.copy(attendanceKnown = false)),
        )
    )

    compose.onNodeWithText("Briefing · 3 volunteers expected").assertIsDisplayed()
    compose.onNodeWithText("Briefing · 2 of 3 arrived").assertDoesNotExist()
    compose.onNodeWithText("Short of 1 volunteer").assertDoesNotExist()
  }

  @Test
  fun running_withTheNextAttendanceFailed_saysHowManyAreExpected() {
    // Its check-ins could not be read, which is not nobody being there either.
    show(
        TeamNowState.Running(
            listOf(sorting),
            onDuty = 4,
            next = listOf(briefing.copy(attendanceFailed = true)),
        )
    )

    compose.onNodeWithText("Briefing · 3 volunteers expected").assertIsDisplayed()
    compose.onNodeWithText("Short of 1 volunteer").assertDoesNotExist()
  }

  @Test
  fun checkIn_whenTheTeamTurnsToRunning_keepsTheConfirmationForItsMoment() {
    // The last volunteer missing is checked in, so the view model reports a team at work. The
    // card follows it to Running without taking the confirmation from under the finger that
    // tapped it, and lets it go when its moment is over rather than when the state changes.
    val state: MutableState<TeamNowState> =
        mutableStateOf(TeamNowState.Missing(listOf(MissingAssignee(sorting, chloe)), onDuty = 3))
    show(state)
    compose.mainClock.autoAdvance = false

    compose.onNodeWithTag(TeamNowCardTestTags.checkIn("m1", "u1")).performClick()
    state.value = TeamNowState.Running(listOf(sorting), onDuty = 4, next = listOf(briefing))
    Snapshot.sendApplyNotifications()
    compose.mainClock.advanceTimeByFrame()

    compose.onNodeWithTag(TeamNowCardTestTags.justCheckedIn("m1", "u1")).assertExists()

    // Once the shell has finished sliding to the new state, the card is the Running card, with
    // her row still on it: checked in, so no longer a target, over the bar of a mission at work.
    compose.mainClock.advanceTimeBy(2_000)

    compose.onNodeWithTag(TeamNowCardTestTags.justCheckedIn("m1", "u1")).assertExists()
    compose.onNodeWithText("Checked in").assertIsDisplayed()
    compose.onNodeWithTag(TeamNowCardTestTags.checkIn("m1", "u1")).assertDoesNotExist()
    compose.onNodeWithText("4 at work").assertIsDisplayed()
    compose.onAllNodesWithTag(TeamNowCardTestTags.TIME_BAR).assertCountEquals(1)
    compose.onNodeWithTag(TeamNowCardTestTags.NEXT).assertIsDisplayed()

    compose.mainClock.advanceTimeBy(JUST_CHECKED_IN_MILLIS)

    compose.onNodeWithTag(TeamNowCardTestTags.justCheckedIn("m1", "u1")).assertDoesNotExist()
    compose.onNodeWithText("Checked in").assertDoesNotExist()
    compose.onNodeWithText("4 at work").assertIsDisplayed()
  }

  @Test
  fun checkIn_tellsScreenReadersWhatATapDoes() {
    // The words are on the row, and so is the action: the whole row is the target, and a reader
    // is told what tapping it does rather than being left with a bare name.
    show(TeamNowState.Missing(listOf(MissingAssignee(sorting, chloe)), onDuty = 3))

    compose.onNodeWithText("Check in").assertIsDisplayed()
    compose
        .onNodeWithTag(TeamNowCardTestTags.checkIn("m1", "u1"))
        .assert(
            SemanticsMatcher("clicks to check Chloé in") {
              it.config.getOrNull(SemanticsActions.OnClick)?.label == "Check in Chloé Dubois"
            }
        )
    // One tap writes a check-in that cannot be changed, so the target stays a full touch target.
    compose.onNodeWithTag(TeamNowCardTestTags.checkIn("m1", "u1")).assertHeightIsAtLeast(48.dp)
  }
}
