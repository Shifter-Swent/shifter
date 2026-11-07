// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.attendance

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.checkin.CheckInMethod
import com.swent.shifter.model.checkin.FakeCheckInRepository
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventRepositoryException
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.event.FakeEventRepository
import com.swent.shifter.model.mission.FakeMissionRepository
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.user.FakeUserRepository
import com.swent.shifter.model.user.User
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The role-aware Overview: which screen the signed-in user is shown, and what happens while that is
 * not known yet. The attendance itself is covered by [ManagerOverviewContentTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class EventOverviewScreenTest {

  @get:Rule val compose = createComposeRule()

  private val start = Instant.parse("2027-06-20T08:00:00Z")
  private val events = FakeEventRepository()
  private val missions = FakeMissionRepository()
  private val teams = FakeTeamRepository()
  private val users = FakeUserRepository(listOf(User("ann", "Ann", "ann@x.org")))
  private val checkIns = FakeCheckInRepository()

  init {
    events.events +=
        Event(
            id = "event-1",
            organizerId = "organizer",
            title = "City Marathon",
            description = "",
            type = EventType.MUSIC,
            startAt = start,
            endAt = start.plus(Duration.ofDays(1)),
            location = EventLocation("Lausanne"),
            createdAt = start,
        )
    teams.teams +=
        Team(
            id = "logistics",
            eventId = "event-1",
            name = "Logistics",
            description = "",
            icon = "truck",
            managerId = "manager",
            createdAt = start,
        )
  }

  @After fun tearDown() = Dispatchers.resetMain()

  /** The screen for [userId], loaded, with a stand-in for the volunteer's planning of #204. */
  private fun show(userId: String, onAddMission: () -> Unit = {}) {
    // Unconfined, so the load the ViewModel starts has answered by the time the screen is read.
    Dispatchers.setMain(UnconfinedTestDispatcher())
    val viewModel = AttendanceViewModel("event-1", userId, events, missions, teams, users, checkIns)
    compose.setContent {
      ShifterTheme {
        EventOverviewScreen(
            viewModel = viewModel,
            volunteerOverview = { Text("Volunteer planning") },
            onAddMission = onAddMission,
        )
      }
    }
  }

  @Test
  fun managerOfATeam_isShownTheAttendanceOfTheEvent() {
    show("manager")

    compose.onNodeWithTag(ManagerOverviewTestTags.SCREEN).assertIsDisplayed()
    compose.onNodeWithTag(ManagerOverviewTestTags.TITLE).assertIsDisplayed()
    compose.onNodeWithText("Volunteer planning").assertDoesNotExist()
  }

  @Test
  fun organizer_isShownTheAttendanceToo() {
    show("organizer")

    compose.onNodeWithTag(ManagerOverviewTestTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun volunteerOfNoTeam_isShownTheirOwnOverviewInstead() {
    show("ann")

    compose.onNodeWithText("Volunteer planning").assertIsDisplayed()
    compose.onNodeWithTag(ManagerOverviewTestTags.SCREEN).assertDoesNotExist()
  }

  @Test
  fun addMission_asAManager_opensTheForm() {
    var opened = 0
    show("manager", onAddMission = { opened++ })

    compose.onNodeWithTag(ManagerOverviewTestTags.ADD_MISSION_BUTTON).performClick()

    assertEquals(1, opened)
  }

  @Test
  fun addMission_asAVolunteerWhoManagesNoTeam_isNotShown() {
    show("ann")

    compose.onNodeWithText("Volunteer planning").assertIsDisplayed()
    compose.onNodeWithTag(ManagerOverviewTestTags.ADD_MISSION_BUTTON).assertDoesNotExist()
  }

  @Test
  fun whileTheRoleIsRead_neitherScreenIsShown() {
    // The dispatcher is left paused, so the load has not come back yet.
    Dispatchers.setMain(StandardTestDispatcher())
    val viewModel =
        AttendanceViewModel("event-1", "manager", events, missions, teams, users, checkIns)
    compose.setContent {
      ShifterTheme {
        EventOverviewScreen(
            viewModel = viewModel,
            volunteerOverview = { Text("Volunteer planning") },
        )
      }
    }

    compose.onNodeWithText("Volunteer planning").assertDoesNotExist()
    compose.onNodeWithTag(ManagerOverviewTestTags.SCREEN).assertDoesNotExist()
  }

  @Test
  fun whenTheRoleCannotBeRead_retryLoadsItAgain() {
    events.failure = EventRepositoryException.Unavailable()
    show("manager")

    // Nothing is assumed about someone whose part in the event is unknown.
    compose.onNodeWithText("Could not load the missions.").assertIsDisplayed()
    compose.onNodeWithTag(ManagerOverviewTestTags.SCREEN).assertDoesNotExist()
    compose.onNodeWithText("Volunteer planning").assertDoesNotExist()

    events.failure = null
    compose.onNodeWithText("Retry").performClick()

    compose.onNodeWithTag(ManagerOverviewTestTags.SCREEN).assertIsDisplayed()
  }

  @Test
  fun leavingTheScreen_sendsTheCheckInStillUndoable() {
    val mission = runningMissionWithAnn()
    var shown by mutableStateOf(true)
    Dispatchers.setMain(UnconfinedTestDispatcher())
    val viewModel =
        AttendanceViewModel("event-1", "manager", events, missions, teams, users, checkIns)
    compose.setContent {
      ShifterTheme { if (shown) EventOverviewScreen(viewModel = viewModel, volunteerOverview = {}) }
    }
    compose.onNodeWithTag(ManagerOverviewTestTags.missionHeader(mission.id)).performClick()
    compose.waitForIdle()
    // Held, so the "Undo" snackbar cannot time out and send the check-in on its own.
    compose.mainClock.autoAdvance = false

    compose.onNodeWithTag(ManagerOverviewTestTags.checkIn(mission.id, "ann")).performClick()
    compose.mainClock.advanceTimeBy(SNACKBAR_SHOWN_MILLIS)
    compose.onNodeWithText("Undo").assertIsDisplayed()
    assertTrue("nothing is sent while it can be undone", checkIns.checkIns.isEmpty())

    // Gone with the screen, the snackbar can no longer send it: only leaving the screen can.
    shown = false
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()

    val sent = checkIns.checkIns.single()
    assertEquals("ann", sent.userId)
    assertEquals(CheckInMethod.MANAGER, sent.method)
  }

  @Test
  fun undoTappedWhileShown_sendsNothing() {
    val mission = runningMissionWithAnn()
    show("manager")
    compose.onNodeWithTag(ManagerOverviewTestTags.missionHeader(mission.id)).performClick()
    compose.waitForIdle()
    compose.mainClock.autoAdvance = false

    compose.onNodeWithTag(ManagerOverviewTestTags.checkIn(mission.id, "ann")).performClick()
    compose.mainClock.advanceTimeBy(SNACKBAR_SHOWN_MILLIS)
    compose.onNodeWithText("Undo").performClick()
    compose.mainClock.advanceTimeBy(AttendanceViewModel.UNDO_TIMEOUT_MILLIS)

    assertTrue(checkIns.checkIns.isEmpty())
    compose.onNodeWithTag(ManagerOverviewTestTags.checkIn(mission.id, "ann")).assertIsDisplayed()
  }

  private companion object {
    /** Long enough for the snackbar to be on screen, far shorter than it stays there. */
    const val SNACKBAR_SHOWN_MILLIS = 500L
  }

  /**
   * A mission of the manager's team running now, as the screen's own clock reads it, with Ann
   * assigned and not checked in.
   */
  private fun runningMissionWithAnn() = runBlocking {
    missions.createMission(
        Mission(
            eventId = "event-1",
            title = "Set up",
            description = "",
            teamId = "logistics",
            volunteersNeeded = 1,
            startAt = Instant.now().minus(Duration.ofMinutes(10)),
            endAt = Instant.now().plus(Duration.ofHours(1)),
            assigneeIds = listOf("ann"),
            createdAt = start,
        )
    )
  }
}
