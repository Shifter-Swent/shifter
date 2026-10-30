// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.attendance

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.checkin.FakeCheckInRepository
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventRepositoryException
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.event.FakeEventRepository
import com.swent.shifter.model.mission.FakeMissionRepository
import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.user.FakeUserRepository
import com.swent.shifter.model.user.User
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
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
  private fun show(userId: String) {
    // Unconfined, so the load the ViewModel starts has answered by the time the screen is read.
    Dispatchers.setMain(UnconfinedTestDispatcher())
    val viewModel = AttendanceViewModel("event-1", userId, events, missions, teams, users, checkIns)
    compose.setContent {
      ShifterTheme {
        EventOverviewScreen(
            viewModel = viewModel,
            volunteerOverview = { Text("Volunteer planning") },
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
}
