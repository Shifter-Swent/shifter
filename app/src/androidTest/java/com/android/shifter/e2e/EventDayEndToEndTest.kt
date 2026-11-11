// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.e2e

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.R
import com.swent.shifter.e2e.EndToEndDriver.Companion.TIMEOUT_MILLIS
import com.swent.shifter.e2e.EndToEndDriver.GoogleAccount
import com.swent.shifter.model.checkin.CheckIn
import com.swent.shifter.model.checkin.CheckInMethod
import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.model.mission.MissionLocation
import com.swent.shifter.ui.attendance.ManagerOverviewTestTags
import com.swent.shifter.ui.checkin.AutoCheckInTestTags
import com.swent.shifter.ui.event.EventCreatedScreenTestTags
import com.swent.shifter.ui.events.MyEventsTestTags
import com.swent.shifter.ui.membership.MembershipRequestsTestTags
import com.swent.shifter.ui.navigation.OrganizerTabs
import com.swent.shifter.ui.overview.OrganizerOverviewScreenTestTags
import com.swent.shifter.ui.people.ManagerDialogTestTags
import com.swent.shifter.ui.people.ParticipantsListTestTags
import com.swent.shifter.ui.people.PeopleScreenTestTags
import com.swent.shifter.ui.planning.PlanningTestTags
import com.swent.shifter.ui.team.TeamSetupScreenTestTags
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The M2 end-to-end event day, through the whole app as three people use it on one phone.
 *
 * An organizer creates an event starting in a few minutes and its team. A future manager and a
 * volunteer apply to the team with its code. The organizer accepts both, appoints the manager, and
 * adds a mission with a location, assigned to the volunteer. The volunteer then finds the mission
 * in their Planning, and is checked in automatically once their position reaches it, but not while
 * they are still away. The manager finally sees them present in the Attendance.
 *
 * Everything goes through the screens and the production repositories, on the Auth and Firestore
 * emulators, driven by [EndToEndDriver]. Only the position is a fake, [MovableLocationProvider], as
 * the GPS cannot be moved; Firestore is read along the way to check what was stored.
 */
@RunWith(AndroidJUnit4::class)
class EventDayEndToEndTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val app = EndToEndDriver(composeTestRule)
  private val repositories = app.repositories
  private val context = app.context

  private val organizer = GoogleAccount("Olga")
  private val manager = GoogleAccount("Maya")
  private val volunteer = GoogleAccount("Victor")

  /** The volunteer's phone, far from the mission at first. */
  private val location = MovableLocationProvider(start = AWAY)

  @After
  fun tearDown() {
    app.cleanUp()
  }

  @Test
  fun eventDay_fromAssignmentToAttendance_showsTheVolunteerPresent() {
    app.launchApp(location)

    // ── 1. The organizer creates an event starting soon and its team, and gets the join code ──
    // Soon, as the check-in opens shortly before a mission starts; a minute is the form's
    // precision.
    val start =
        LocalDateTime.now(ZoneId.systemDefault()).plusMinutes(10).truncatedTo(ChronoUnit.MINUTES)
    val organizerId = app.signInAs(organizer)
    app.click(MyEventsTestTags.ORGANIZER_VIEW_BUTTON)
    app.click(MyEventsTestTags.CREATE_EVENT_BUTTON)
    app.fillAndSubmitEventForm("Paléo Festival", start = start, end = start.plusDays(1))

    app.waitForNode(TeamSetupScreenTestTags.ACTIVE_FORM)
    val event = runBlocking {
      repositories.eventRepository.getEventsByOrganizer(organizerId).single()
    }
    app.createdPaths += "events/${event.id}"
    assertEquals(start.atZone(ZoneId.systemDefault()).toInstant(), event.startAt)

    app.createTeam("Welcome")
    val team = runBlocking { repositories.teamRepository.getTeamsByEvent(event.id).single() }
    // Deleted before its event, as Firestore does not cascade the deletion.
    app.createdPaths.add(0, "events/${event.id}/teams/${team.id}")
    app.waitForNode(TeamSetupScreenTestTags.savedTeam(team.id))
    app.click(TeamSetupScreenTestTags.PROCEED_BUTTON)

    app.waitForNode(EventCreatedScreenTestTags.JOIN_CODE)
    val joinCode = app.textOf(composeTestRule.onNodeWithTag(EventCreatedScreenTestTags.JOIN_CODE))
    app.click(EventCreatedScreenTestTags.DONE_BUTTON)
    app.waitForNode(MyEventsTestTags.eventCard(event.id))
    app.signOut()

    // ── 2. The future manager and the volunteer apply to the team with the code ──
    val managerId = applyAs(manager, joinCode, event.title, team.id)
    val volunteerId = applyAs(volunteer, joinCode, event.title, team.id)
    app.createdPaths.add(0, "eventParticipants/${event.id}")
    for (id in listOf(managerId, volunteerId)) {
      app.createdPaths.add(0, "events/${event.id}/membershipRequests/$id")
    }

    // ── 3. The organizer accepts both, and appoints the manager of the team ──
    app.signInAs(organizer)
    app.click(MyEventsTestTags.ORGANIZER_VIEW_BUTTON)
    app.click(MyEventsTestTags.eventAction(event.id))
    app.click(OrganizerTabs.People.testTag)
    app.click(PeopleScreenTestTags.REQUESTS_TAB)
    for (id in listOf(managerId, volunteerId)) {
      app.click(MembershipRequestsTestTags.acceptButton(id))
      composeTestRule.waitUntil(TIMEOUT_MILLIS) {
        app.textOrNull(MembershipRequestsTestTags.status(id)) ==
            context.getString(R.string.membership_requests_status_accepted)
      }
    }

    app.click(PeopleScreenTestTags.PARTICIPANTS_TAB)
    app.click(ParticipantsListTestTags.participantCard(managerId))
    app.click(ParticipantsListTestTags.managerButton(managerId))
    app.click(ManagerDialogTestTags.CONFIRM_BUTTON)
    // The card merges its badge into itself.
    app.waitForNode(ParticipantsListTestTags.managerBadge(managerId), useUnmergedTree = true)
    val managed = runBlocking { repositories.teamRepository.getTeam(event.id, team.id) }
    assertEquals(managerId, managed?.managerId)
    // A manager leads the members without being one of them: only the volunteer can be assigned.
    assertEquals(listOf(volunteerId), managed?.memberIds)

    // ── 4. The organizer adds a mission with a location, assigned to the volunteer ──
    app.click(OrganizerTabs.Overview.testTag)
    app.click(OrganizerOverviewScreenTestTags.ADD_MISSION_BUTTON)
    app.addMissionForTeam(
        team.id,
        "Welcome desk",
        startHour = start.hour,
        location = DESK,
        assigneeId = volunteerId,
    )

    app.waitForNode(OrganizerOverviewScreenTestTags.SCREEN)
    val mission = runBlocking {
      repositories.missionRepository.getMissionsByEvent(event.id).single()
    }
    // Deleted before its event, and its check-ins before it.
    app.createdPaths.add(0, "events/${event.id}/missions/${mission.id}")
    app.createdPaths.add(0, "events/${event.id}/missions/${mission.id}/checkIns/$volunteerId")
    assertEquals("Welcome desk", mission.title)
    assertEquals(DESK, mission.location)
    assertEquals(listOf(volunteerId), mission.assigneeIds)
    assertEquals(event.startAt, mission.startAt)
    assertEquals(mission.startAt.plus(Duration.ofHours(1)), mission.endAt)
    app.waitForNode(OrganizerOverviewScreenTestTags.mission(mission.id))
    app.backTo(MyEventsTestTags.ORGANIZER_SCREEN)
    app.signOut()

    // ── 5. The volunteer finds the mission in their Planning, and is checked in on arrival ──
    app.signInAs(volunteer)
    app.click(MyEventsTestTags.eventCard(event.id))
    app.waitForNode(PlanningTestTags.mission(mission.id))
    composeTestRule
        .onNodeWithTag(PlanningTestTags.address(mission.id), useUnmergedTree = true)
        .assertTextContains(DESK.address)
    app.waitForNode(AutoCheckInTestTags.CARD)

    // Away from the desk, the positions read leave the volunteer unchecked.
    val readAway = location.emitted.get()
    composeTestRule.waitUntil(TIMEOUT_MILLIS) { location.emitted.get() >= readAway + 3 }
    assertNoNode(AutoCheckInTestTags.CONFIRMATION)
    assertNoNode(PlanningTestTags.checkIn(mission.id))
    assertEquals(emptyList<CheckIn>(), checkInsOf(event.id, volunteerId))

    // At the desk, they are checked in on their own, and the Planning says so.
    location.position = DESK.position
    app.waitForNode(AutoCheckInTestTags.CONFIRMATION)
    app.waitForNode(PlanningTestTags.checkIn(mission.id), useUnmergedTree = true)
    val checkIn = runBlocking {
      withTimeout(TIMEOUT_MILLIS) {
        repositories.checkInRepository
            .observeMyCheckIns(event.id, volunteerId)
            .first { checkIns -> checkIns.any { !it.pending } }
            .single()
      }
    }
    assertEquals(mission.id, checkIn.missionId)
    assertEquals(CheckInMethod.SELF, checkIn.method)
    assertEquals(volunteerId, checkIn.checkedInBy)
    app.backTo(MyEventsTestTags.STAFF_SCREEN)
    app.signOut()

    // ── 6. The manager sees the volunteer present in the Attendance ──
    app.signInAs(manager)
    app.click(MyEventsTestTags.eventCard(event.id))
    app.waitForNode(ManagerOverviewTestTags.SCREEN)
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      // The card's header merges its counter into itself.
      app.textOrNull(ManagerOverviewTestTags.counter(mission.id), useUnmergedTree = true) ==
          context.getString(R.string.attendance_counter, "1", 1)
    }
    app.click(ManagerOverviewTestTags.missionHeader(mission.id))
    app.waitForNode(ManagerOverviewTestTags.present(mission.id))
    composeTestRule
        .onNode(
            hasText(volunteer.name) and
                hasAnyAncestor(hasTestTag(ManagerOverviewTestTags.present(mission.id))),
            useUnmergedTree = true,
        )
        .assertExists()
    assertNoNode(ManagerOverviewTestTags.expected(mission.id))
    assertNoNode(ManagerOverviewTestTags.missing(mission.id))
    composeTestRule
        .onNodeWithTag(ManagerOverviewTestTags.counter(mission.id), useUnmergedTree = true)
        .assertTextEquals(context.getString(R.string.attendance_counter, "1", 1))
  }

  /** Signs [account] in, applies to [teamId] with [joinCode], signs out, and returns their id. */
  private fun applyAs(
      account: GoogleAccount,
      joinCode: String,
      eventTitle: String,
      teamId: String,
  ): String {
    val id = app.signInAs(account)
    app.waitForNode(MyEventsTestTags.EMPTY_STATE)
    app.applyWithCode(joinCode, eventTitle, teamId)
    app.backTo(MyEventsTestTags.STAFF_SCREEN)
    app.signOut()
    return id
  }

  /** The check-ins of [userId] in [eventId], as Firestore has them now. */
  private fun checkInsOf(eventId: String, userId: String): List<CheckIn> = runBlocking {
    withTimeout(TIMEOUT_MILLIS) {
      repositories.checkInRepository.observeMyCheckIns(eventId, userId).first()
    }
  }

  private fun assertNoNode(tag: String) {
    assertTrue(
        "Unexpected node $tag",
        composeTestRule
            .onAllNodesWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isEmpty(),
    )
  }

  private companion object {
    /** Where the welcome desk is, in Nyon. */
    val DESK = MissionLocation("Place du Château, Nyon", GeoPosition(46.3820, 6.2390))

    /** About 2 km north of the desk, far outside the check-in radius. */
    val AWAY = GeoPosition(46.4000, 6.2390)
  }
}
