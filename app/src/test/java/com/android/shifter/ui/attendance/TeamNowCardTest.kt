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

  private fun mission(id: String, title: String) =
      AttendanceMission(
          id = id,
          title = title,
          teamId = "team",
          teamName = "Logistics",
          volunteersNeeded = 2,
          startAt = Instant.parse("2027-06-20T14:00:00Z"),
          endAt = Instant.parse("2027-06-20T15:30:00Z"),
          assignees = emptyList(),
          attendanceKnown = true,
      )

  private fun show(
      state: TeamNowState.Missing,
      onCheckIn: (String, String) -> Unit = { _, _ -> },
      onViewOnMap: () -> Unit = {},
  ) {
    show(mutableStateOf(state), onCheckIn, onViewOnMap)
  }

  /** The card on a state the test can refresh, as the view model does once a check-in lands. */
  private fun show(
      state: MutableState<TeamNowState.Missing>,
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
  }

  @Test
  fun missing_countsPeopleRatherThanRows() {
    // One volunteer missing from two missions is one person to find, listed under each.
    show(
        TeamNowState.Missing(
            listOf(MissingAssignee(sorting, chloe), MissingAssignee(welcome, chloe)),
            onDuty = 0,
        )
    )

    compose.onNodeWithText("1 volunteer missing").assertIsDisplayed()
  }

  @Test
  fun missing_withTwoVolunteers_saysTwoAndShowsARowEach() {
    show(
        TeamNowState.Missing(
            listOf(MissingAssignee(sorting, chloe), MissingAssignee(welcome, daniel)),
            onDuty = 1,
        )
    )

    compose.onNodeWithText("2 volunteers missing").assertIsDisplayed()
    compose.onNodeWithTag(TeamNowCardTestTags.missing("m1", "u1")).assertIsDisplayed()
    compose.onNodeWithTag(TeamNowCardTestTags.missing("m2", "u2")).assertIsDisplayed()
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
  fun checkIn_keepsTheVolunteerInGreenForAMoment() {
    show(TeamNowState.Missing(listOf(MissingAssignee(sorting, chloe)), onDuty = 3))
    compose.mainClock.autoAdvance = false

    compose.onNodeWithTag(TeamNowCardTestTags.checkIn("m1", "u1")).performClick()
    compose.mainClock.advanceTimeByFrame()

    compose.onNodeWithTag(TeamNowCardTestTags.justCheckedIn("m1", "u1")).assertExists()
    compose.onNodeWithTag(TeamNowCardTestTags.missing("m1", "u1")).assertDoesNotExist()
    // Checked in, their row can no longer be tapped: the check-in cannot be made twice.
    compose.onNodeWithTag(TeamNowCardTestTags.checkIn("m1", "u1")).assertDoesNotExist()

    compose.mainClock.advanceTimeBy(JUST_CHECKED_IN_MILLIS + 1_000)

    compose.onNodeWithTag(TeamNowCardTestTags.justCheckedIn("m1", "u1")).assertDoesNotExist()
    compose.onNodeWithTag(TeamNowCardTestTags.missing("m1", "u1")).assertExists()
  }

  @Test
  fun checkIn_whenTheStateDropsThem_takesTheRowAwayAfterTheMoment() {
    // What the interactive preview does: the view model stops reporting the volunteer missing as
    // soon as the check-in shows, so once the green moment is over nothing is left of their row.
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
    state.value = TeamNowState.Missing(listOf(MissingAssignee(sorting, daniel)), onDuty = 4)
    Snapshot.sendApplyNotifications()
    compose.mainClock.advanceTimeByFrame()

    compose.onNodeWithTag(TeamNowCardTestTags.justCheckedIn("m1", "u1")).assertExists()

    compose.mainClock.advanceTimeBy(JUST_CHECKED_IN_MILLIS + 1_000)

    compose.onNodeWithTag(TeamNowCardTestTags.justCheckedIn("m1", "u1")).assertDoesNotExist()
    compose.onNodeWithTag(TeamNowCardTestTags.missing("m1", "u1")).assertDoesNotExist()
    // The volunteer the state still reports keeps their row.
    compose.onNodeWithTag(TeamNowCardTestTags.missing("m1", "u2")).assertExists()
  }

  @Test
  fun missing_withOthersCheckedIn_saysHowMany() {
    show(TeamNowState.Missing(listOf(MissingAssignee(sorting, chloe)), onDuty = 3))

    compose.onNodeWithText("3 others checked in").assertIsDisplayed()
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
  fun missing_showsATimeBarPerMissionConcerned() {
    // Two missions, three rows: a mission with two people missing still has one bar.
    show(
        TeamNowState.Missing(
            listOf(
                MissingAssignee(sorting, chloe),
                MissingAssignee(sorting, noah),
                MissingAssignee(welcome, daniel),
            ),
            onDuty = 0,
        )
    )

    compose.onAllNodesWithTag(TeamNowCardTestTags.TIME_BAR).assertCountEquals(2)
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
  fun missing_withNamesTheProfileCannotGive_stillReadsTheRowAndItsInitial() {
    val unnamed = AttendanceVolunteer("u8", null)
    // A name whose first letter is outside the basic plane: its initial is a whole code point,
    // never half a surrogate pair.
    val astral = AttendanceVolunteer("u9", "𝐀lice Martin")
    show(
        TeamNowState.Missing(
            listOf(MissingAssignee(sorting, unnamed), MissingAssignee(sorting, astral)),
            onDuty = 0,
        )
    )

    compose.onNodeWithText("Unnamed volunteer").assertIsDisplayed()
    compose.onNodeWithText("?").assertIsDisplayed()
    compose.onNodeWithText("𝐀").assertIsDisplayed()
  }

  @Test
  fun checkIn_leavesTheCountToTheState() {
    // The card acknowledges the tap in green, but what is missing is the state's to say: the
    // headline must not read zero while the state still reports someone missing.
    show(TeamNowState.Missing(listOf(MissingAssignee(sorting, chloe)), onDuty = 3))
    compose.mainClock.autoAdvance = false

    compose.onNodeWithTag(TeamNowCardTestTags.checkIn("m1", "u1")).performClick()
    compose.mainClock.advanceTimeByFrame()

    compose.onNodeWithTag(TeamNowCardTestTags.justCheckedIn("m1", "u1")).assertExists()
    compose.onNodeWithText("1 volunteer missing").assertExists()
    compose.onNodeWithText("0 volunteers missing").assertDoesNotExist()
  }

  @Test
  fun checkIn_withAVolunteerMissingFromTwoMissions_takesOneMissionAtATime() {
    val missions = mutableListOf<String>()
    show(
        TeamNowState.Missing(
            listOf(MissingAssignee(sorting, chloe), MissingAssignee(welcome, chloe)),
            onDuty = 3,
        ),
        onCheckIn = { missionId, _ -> missions += missionId },
    )
    compose.mainClock.autoAdvance = false

    compose.onNodeWithTag(TeamNowCardTestTags.checkIn("m1", "u1")).performClick()
    compose.mainClock.advanceTimeByFrame()

    compose.onNodeWithTag(TeamNowCardTestTags.justCheckedIn("m1", "u1")).assertExists()
    // She is still missing from the other mission, and is still checked in to it on its own.
    compose.onNodeWithTag(TeamNowCardTestTags.missing("m2", "u1")).assertExists()
    compose.onNodeWithTag(TeamNowCardTestTags.justCheckedIn("m2", "u1")).assertDoesNotExist()

    compose.onNodeWithTag(TeamNowCardTestTags.checkIn("m2", "u1")).performClick()
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
                listOf(
                    MissingAssignee(sorting, chloe),
                    MissingAssignee(welcome, chloe),
                    MissingAssignee(sorting, daniel),
                ),
                onDuty = 3,
            )
        )
    show(state)
    compose.mainClock.autoAdvance = false

    compose.onNodeWithTag(TeamNowCardTestTags.checkIn("m1", "u1")).performClick()
    // The view model drops the row it wrote, and still reports her missing from the other one.
    state.value =
        TeamNowState.Missing(
            listOf(MissingAssignee(welcome, chloe), MissingAssignee(sorting, daniel)),
            onDuty = 3,
        )
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
