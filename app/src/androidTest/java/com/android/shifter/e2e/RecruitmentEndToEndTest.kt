// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.e2e

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.R
import com.swent.shifter.e2e.EndToEndDriver.Companion.TIMEOUT_MILLIS
import com.swent.shifter.e2e.EndToEndDriver.GoogleAccount
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.ui.event.EventCreatedScreenTestTags
import com.swent.shifter.ui.events.MyEventsTestTags
import com.swent.shifter.ui.membership.MembershipRequestsTestTags
import com.swent.shifter.ui.mission.AddMissionScreenTestTags
import com.swent.shifter.ui.navigation.OrganizerTabs
import com.swent.shifter.ui.overview.OrganizerOverviewScreenTestTags
import com.swent.shifter.ui.people.ParticipantsListTestTags
import com.swent.shifter.ui.people.PeopleScreenTestTags
import com.swent.shifter.ui.team.TeamSetupScreenTestTags
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The M1 end-to-end recruitment flow, through the whole app as two people use it on one phone.
 *
 * An organizer signs in, creates an event and its team, and reads its join code. A volunteer then
 * signs in on the same phone, finds the event by that code and applies to the team. The organizer
 * comes back, adds a mission for the team from the event's Overview, accepts the request and sees
 * the volunteer among the team's participants, and the volunteer finally sees the event confirmed
 * in My Events.
 *
 * Everything goes through the screens and the production repositories, on the Auth and Firestore
 * emulators, driven by [EndToEndDriver]. Firestore is read along the way to check that what the
 * screens show is what was stored.
 */
@RunWith(AndroidJUnit4::class)
class RecruitmentEndToEndTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val app = EndToEndDriver(composeTestRule)
  private val repositories = app.repositories
  private val context = app.context

  private val organizer = GoogleAccount("Olga")
  private val volunteer = GoogleAccount("Victor")

  @After
  fun tearDown() {
    app.cleanUp()
  }

  @Test
  fun recruitment_fromEventCreationToAcceptance_confirmsTheVolunteer() {
    app.launchApp()

    // ── 1. The organizer creates the event and its team, and gets the join code ──
    val organizerId = app.signInAs(organizer)
    app.click(MyEventsTestTags.ORGANIZER_VIEW_BUTTON)
    app.click(MyEventsTestTags.CREATE_EVENT_BUTTON)
    app.fillAndSubmitEventForm(
        "Paléo Festival",
        start = LocalDateTime.of(2099, 7, 21, EVENT_START_HOUR, 0),
        end = LocalDateTime.of(2099, 7, 26, 23, 30),
    )

    app.waitForNode(TeamSetupScreenTestTags.ACTIVE_FORM)
    val event = runBlocking {
      repositories.eventRepository.getEventsByOrganizer(organizerId).single()
    }
    app.createdPaths += "events/${event.id}"
    assertEquals("Paléo Festival", event.title)

    app.createTeam("Logistics")
    val team = runBlocking { repositories.teamRepository.getTeamsByEvent(event.id).single() }
    // Deleted before its event, as Firestore does not cascade the deletion.
    app.createdPaths.add(0, "events/${event.id}/teams/${team.id}")
    app.waitForNode(TeamSetupScreenTestTags.savedTeam(team.id))
    app.click(TeamSetupScreenTestTags.PROCEED_BUTTON)

    app.waitForNode(EventCreatedScreenTestTags.JOIN_CODE)
    // The code the volunteer types is the one the screen shows, which is the event's.
    val joinCode = app.textOf(composeTestRule.onNodeWithTag(EventCreatedScreenTestTags.JOIN_CODE))
    assertEquals(event.joinCode, joinCode)
    app.click(EventCreatedScreenTestTags.DONE_BUTTON)
    app.waitForNode(MyEventsTestTags.eventCard(event.id))
    app.signOut()

    // ── 2. A volunteer finds the event by its code and applies to the team ──
    val volunteerId = app.signInAs(volunteer)
    // Deleted before the event, like everything the acceptance writes below.
    app.createdPaths.add(0, "events/${event.id}/membershipRequests/$volunteerId")
    app.createdPaths.add(0, "eventParticipants/${event.id}")
    app.waitForNode(MyEventsTestTags.EMPTY_STATE)
    app.applyWithCode(joinCode, event.title, team.id)

    assertEquals(MembershipRequestStatus.PENDING, requestOf(event.id, volunteerId).status)
    app.backTo(MyEventsTestTags.STAFF_SCREEN)
    // The list reloads on return and shows the event, awaiting the organizer's answer.
    app.waitForNode(MyEventsTestTags.eventCard(event.id))
    composeTestRule
        .onNodeWithTag(MyEventsTestTags.statusPill(event.id))
        .assertTextEquals(app.badge(R.string.my_events_badge_pending_approval))
    app.signOut()

    // ── 3. The organizer plans a mission for the team on the event's Overview ──
    app.signInAs(organizer)
    app.click(MyEventsTestTags.ORGANIZER_VIEW_BUTTON)
    app.waitForNode(MyEventsTestTags.eventAction(event.id))
    app.click(MyEventsTestTags.eventAction(event.id))
    app.waitForNode(OrganizerOverviewScreenTestTags.EMPTY_MISSIONS)
    // The team created in Team Setup is offered as a filter of the event's missions.
    app.waitForNode(OrganizerOverviewScreenTestTags.teamChip(team.id))

    app.click(OrganizerOverviewScreenTestTags.ADD_MISSION_BUTTON)
    app.waitForNode(AddMissionScreenTestTags.TITLE_FIELD)
    // The event title can join the merged tree after the form, so wait for it in the unmerged one.
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      composeTestRule
          .onAllNodesWithTag(AddMissionScreenTestTags.EVENT_TITLE, useUnmergedTree = true)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    composeTestRule
        .onNodeWithTag(AddMissionScreenTestTags.EVENT_TITLE, useUnmergedTree = true)
        .assertTextContains(event.title)
    app.addMissionForTeam(team.id, "Set up the stage", EVENT_START_HOUR)

    // Saving the mission returns to the Overview, which lists it among the missions to do.
    app.waitForNode(OrganizerOverviewScreenTestTags.SCREEN)
    val mission = runBlocking {
      repositories.missionRepository.getMissionsByEvent(event.id).single()
    }
    // Deleted before its event, as Firestore does not cascade the deletion.
    app.createdPaths.add(0, "events/${event.id}/missions/${mission.id}")
    assertEquals("Set up the stage", mission.title)
    assertEquals(team.id, mission.teamId)
    assertEquals(event.startAt, mission.startAt)
    assertEquals(mission.startAt.plus(Duration.ofHours(1)), mission.endAt)
    app.waitForNode(OrganizerOverviewScreenTestTags.mission(mission.id))
    composeTestRule
        .onNodeWithTag(OrganizerOverviewScreenTestTags.PROGRESS_COUNT)
        .assertTextEquals(context.getString(R.string.overview_progress_count, 0, 1))
    // Filtering on its team still shows it.
    app.click(OrganizerOverviewScreenTestTags.teamChip(team.id))
    app.waitForNode(OrganizerOverviewScreenTestTags.mission(mission.id))

    // ── 4. The organizer accepts the request and finds the volunteer in the team ──
    app.click(OrganizerTabs.People.testTag)
    app.click(PeopleScreenTestTags.REQUESTS_TAB)

    app.waitForNode(MembershipRequestsTestTags.acceptButton(volunteerId))
    app.click(MembershipRequestsTestTags.acceptButton(volunteerId))
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      app.textOrNull(MembershipRequestsTestTags.status(volunteerId)) ==
          context.getString(R.string.membership_requests_status_accepted)
    }

    val accepted = requestOf(event.id, volunteerId)
    assertEquals(MembershipRequestStatus.ACCEPTED, accepted.status)
    assertEquals(team.id, accepted.teamId)
    val acceptedTeam = runBlocking { repositories.teamRepository.getTeam(event.id, team.id) }
    assertEquals(listOf(volunteerId), acceptedTeam?.memberIds)

    app.click(PeopleScreenTestTags.PARTICIPANTS_TAB)
    app.waitForNode(ParticipantsListTestTags.participantCard(volunteerId))
    // The volunteer is counted in the team they applied to, its only member.
    composeTestRule
        .onNodeWithTag(ParticipantsListTestTags.teamBar(team.id))
        .assertTextContains("· 1", substring = true)
    app.backTo(MyEventsTestTags.ORGANIZER_SCREEN)
    app.signOut()

    // ── 5. The volunteer sees the event confirmed ──
    app.signInAs(volunteer)
    app.waitForNode(MyEventsTestTags.eventCard(event.id))
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      app.textOrNull(MyEventsTestTags.statusPill(event.id)) ==
          app.badge(R.string.my_events_badge_confirmed)
    }
  }

  private fun requestOf(eventId: String, userId: String) =
      checkNotNull(
          runBlocking {
            repositories.membershipRequestRepository.getMembershipRequest(eventId, userId)
          }
      )

  private companion object {
    /** Hour the event starts at, which the mission's time pickers open on. */
    const val EVENT_START_HOUR = 14
  }
}
