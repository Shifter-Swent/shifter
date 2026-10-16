// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.e2e

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.swent.shifter.R
import com.swent.shifter.firebase.AuthEmulator
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
import com.swent.shifter.model.RepositoryProviderFactory
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.ui.authentication.SignInScreenTestTags
import com.swent.shifter.ui.event.CreateEventScreenTestTags
import com.swent.shifter.ui.event.EventCreatedScreenTestTags
import com.swent.shifter.ui.event.JoinEventScreenTestTags
import com.swent.shifter.ui.events.MyEventsTestTags
import com.swent.shifter.ui.membership.MembershipRequestsTestTags
import com.swent.shifter.ui.mission.AddMissionScreenTestTags
import com.swent.shifter.ui.navigation.OrganizerTabs
import com.swent.shifter.ui.navigation.ShifterNavHost
import com.swent.shifter.ui.overview.OrganizerOverviewScreenTestTags
import com.swent.shifter.ui.people.ParticipantsListTestTags
import com.swent.shifter.ui.people.PeopleScreenTestTags
import com.swent.shifter.ui.settings.SettingsScreenTestTags
import com.swent.shifter.ui.team.CreateTeamFormTestTags
import com.swent.shifter.ui.team.TeamIcon
import com.swent.shifter.ui.team.TeamSetupScreenTestTags
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
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
 * emulators. Only the Google account picker is replaced, as the system one cannot be driven: it
 * answers with whichever account [signInAs] is about to use. Firestore is read along the way to
 * check that what the screens show is what was stored.
 */
@RunWith(AndroidJUnit4::class)
class RecruitmentEndToEndTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val repositories =
      RepositoryProviderFactory.create(FirestoreEmulator.firestore, AuthEmulator.auth)

  private val context: Context
    get() = InstrumentationRegistry.getInstrumentation().targetContext

  private val organizer = GoogleAccount("Olga")
  private val volunteer = GoogleAccount("Victor")

  /** The account the picker answers with on the next sign-in. */
  private var pickedAccount: GoogleAccount? = null

  private val accountPicker =
      object :
          CredentialManager by CredentialManager.create(
              InstrumentationRegistry.getInstrumentation().targetContext
          ) {
        override suspend fun getCredential(
            context: Context,
            request: GetCredentialRequest,
        ): GetCredentialResponse =
            GetCredentialResponse(checkNotNull(pickedAccount) { "No account picked" }.credential())
      }

  /** Documents written by the test, deleted afterwards, children before their parents. */
  private val createdPaths = mutableListOf<String>()

  @Before
  fun setUp() {
    AuthEmulator.auth.signOut()
  }

  @After
  fun tearDown() {
    runBlocking { createdPaths.forEach { FirestoreEmulatorAdmin.deleteDocument(it) } }
    AuthEmulator.auth.signOut()
    AuthEmulator.clearAccounts()
  }

  @Test
  fun recruitment_fromEventCreationToAcceptance_confirmsTheVolunteer() {
    composeTestRule.setContent {
      ShifterTheme {
        ShifterNavHost(
            isSignedIn = false,
            repositories = repositories,
            credentialManager = accountPicker,
        )
      }
    }

    // ── 1. The organizer creates the event and its team, and gets the join code ──
    val organizerId = signInAs(organizer)
    click(MyEventsTestTags.ORGANIZER_VIEW_BUTTON)
    click(MyEventsTestTags.CREATE_EVENT_BUTTON)
    fillAndSubmitEventForm()

    waitForNode(TeamSetupScreenTestTags.ACTIVE_FORM)
    val event = runBlocking {
      repositories.eventRepository.getEventsByOrganizer(organizerId).single()
    }
    createdPaths += "events/${event.id}"
    assertEquals("Paléo Festival", event.title)

    createTeam("Logistics")
    val team = runBlocking { repositories.teamRepository.getTeamsByEvent(event.id).single() }
    // Deleted before its event, as Firestore does not cascade the deletion.
    createdPaths.add(0, "events/${event.id}/teams/${team.id}")
    waitForNode(TeamSetupScreenTestTags.savedTeam(team.id))
    click(TeamSetupScreenTestTags.PROCEED_BUTTON)

    waitForNode(EventCreatedScreenTestTags.JOIN_CODE)
    // The code the volunteer types is the one the screen shows, which is the event's.
    val joinCode = textOf(composeTestRule.onNodeWithTag(EventCreatedScreenTestTags.JOIN_CODE))
    assertEquals(event.joinCode, joinCode)
    click(EventCreatedScreenTestTags.DONE_BUTTON)
    waitForNode(MyEventsTestTags.eventCard(event.id))
    signOut()

    // ── 2. A volunteer finds the event by its code and applies to the team ──
    val volunteerId = signInAs(volunteer)
    // Deleted before the event, like everything the acceptance writes below.
    createdPaths.add(0, "events/${event.id}/membershipRequests/$volunteerId")
    createdPaths.add(0, "eventParticipants/${event.id}")
    waitForNode(MyEventsTestTags.EMPTY_STATE)
    click(MyEventsTestTags.SCAN_QR_BUTTON)
    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.JOIN_CODE_FIELD)
        .performTextInput(joinCode)
    click(JoinEventScreenTestTags.FIND_BUTTON)

    waitForNode(JoinEventScreenTestTags.teamOption(team.id))
    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.EVENT_TITLE)
        .assertTextContains(event.title)
    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.teamOption(team.id))
        .performScrollTo()
        .performClick()
    // The button stays pinned under the event, out of the scrolling part.
    click(JoinEventScreenTestTags.APPLY_BUTTON)
    waitForNode(JoinEventScreenTestTags.APPLIED_MESSAGE)

    assertEquals(MembershipRequestStatus.PENDING, requestOf(event.id, volunteerId).status)
    backTo(MyEventsTestTags.STAFF_SCREEN)
    // The list reloads on return and shows the event, awaiting the organizer's answer.
    waitForNode(MyEventsTestTags.eventCard(event.id))
    composeTestRule
        .onNodeWithTag(MyEventsTestTags.statusPill(event.id))
        .assertTextEquals(badge(R.string.my_events_badge_pending_approval))
    signOut()

    // ── 3. The organizer plans a mission for the team on the event's Overview ──
    signInAs(organizer)
    click(MyEventsTestTags.ORGANIZER_VIEW_BUTTON)
    waitForNode(MyEventsTestTags.eventAction(event.id))
    click(MyEventsTestTags.eventAction(event.id))
    waitForNode(OrganizerOverviewScreenTestTags.EMPTY_MISSIONS)
    // The team created in Team Setup is offered as a filter of the event's missions.
    waitForNode(OrganizerOverviewScreenTestTags.teamChip(team.id))

    click(OrganizerOverviewScreenTestTags.ADD_MISSION_BUTTON)
    waitForNode(AddMissionScreenTestTags.TITLE_FIELD)
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
    addMissionForTeam(team.id, "Set up the stage")

    // Saving the mission returns to the Overview, which lists it among the missions to do.
    waitForNode(OrganizerOverviewScreenTestTags.SCREEN)
    val mission = runBlocking {
      repositories.missionRepository.getMissionsByEvent(event.id).single()
    }
    // Deleted before its event, as Firestore does not cascade the deletion.
    createdPaths.add(0, "events/${event.id}/missions/${mission.id}")
    assertEquals("Set up the stage", mission.title)
    assertEquals(team.id, mission.teamId)
    assertEquals(event.startAt, mission.startAt)
    assertEquals(mission.startAt.plus(Duration.ofHours(1)), mission.endAt)
    waitForNode(OrganizerOverviewScreenTestTags.mission(mission.id))
    composeTestRule
        .onNodeWithTag(OrganizerOverviewScreenTestTags.PROGRESS_COUNT)
        .assertTextEquals(context.getString(R.string.overview_progress_count, 0, 1))
    // Filtering on its team still shows it.
    click(OrganizerOverviewScreenTestTags.teamChip(team.id))
    waitForNode(OrganizerOverviewScreenTestTags.mission(mission.id))

    // ── 4. The organizer accepts the request and finds the volunteer in the team ──
    click(OrganizerTabs.People.testTag)
    click(PeopleScreenTestTags.REQUESTS_TAB)

    waitForNode(MembershipRequestsTestTags.acceptButton(volunteerId))
    click(MembershipRequestsTestTags.acceptButton(volunteerId))
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      textOrNull(MembershipRequestsTestTags.status(volunteerId)) ==
          context.getString(R.string.membership_requests_status_accepted)
    }

    val accepted = requestOf(event.id, volunteerId)
    assertEquals(MembershipRequestStatus.ACCEPTED, accepted.status)
    assertEquals(team.id, accepted.teamId)
    val acceptedTeam = runBlocking { repositories.teamRepository.getTeam(event.id, team.id) }
    assertEquals(listOf(volunteerId), acceptedTeam?.memberIds)

    click(PeopleScreenTestTags.PARTICIPANTS_TAB)
    waitForNode(ParticipantsListTestTags.participantCard(volunteerId))
    // The volunteer is counted in the team they applied to, its only member.
    composeTestRule
        .onNodeWithTag(ParticipantsListTestTags.teamBar(team.id))
        .assertTextContains("· 1", substring = true)
    backTo(MyEventsTestTags.ORGANIZER_SCREEN)
    signOut()

    // ── 5. The volunteer sees the event confirmed ──
    signInAs(volunteer)
    waitForNode(MyEventsTestTags.eventCard(event.id))
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      textOrNull(MyEventsTestTags.statusPill(event.id)) == badge(R.string.my_events_badge_confirmed)
    }
  }

  /**
   * Signs in through the sign-in screen, the picker answering with [account], and waits for the
   * volunteer events every sign-in opens on. Returns the user's id and registers their profile for
   * cleanup.
   */
  private fun signInAs(account: GoogleAccount): String {
    pickedAccount = account
    waitForNode(SignInScreenTestTags.LOGIN_BUTTON)
    click(SignInScreenTestTags.LOGIN_BUTTON)
    waitForNode(MyEventsTestTags.STAFF_SCREEN)
    val uid = checkNotNull(repositories.authRepository.currentUser()).uid
    if ("users/$uid" !in createdPaths) {
      createdPaths += "users/$uid"
      createdPaths += "publicProfiles/$uid"
    }
    return uid
  }

  /** Signs out from Settings, opened from the avatar of the events list on screen. */
  private fun signOut() {
    click(MyEventsTestTags.AVATAR)
    click(SettingsScreenTestTags.SIGN_OUT_BUTTON)
    waitForNode(SignInScreenTestTags.LOGIN_BUTTON)
  }

  /** Fills every field of the event creation form with valid values, and submits it. */
  private fun fillAndSubmitEventForm() {
    fun field(tag: String) = composeTestRule.onNodeWithTag(tag).performScrollTo()
    field(CreateEventScreenTestTags.TITLE_FIELD).performTextInput("Paléo Festival")
    field(CreateEventScreenTestTags.DESCRIPTION_FIELD).performTextInput("Open-air festival")
    field(CreateEventScreenTestTags.typeChip(EventType.MUSIC)).performClick()
    field(CreateEventScreenTestTags.ADDRESS_FIELD).performTextInput("Nyon")
    field(CreateEventScreenTestTags.START_FIELD).performTextInput("21/07/2099 $EVENT_START_HOUR:00")
    field(CreateEventScreenTestTags.END_FIELD).performTextInput("26/07/2099 23:30")
    field(CreateEventScreenTestTags.SUBMIT_BUTTON).performClick()
  }

  /**
   * Fills the mission form for [teamId], from the event's start to an hour later, and saves it. The
   * start picker opens on the event's start; the end one is moved an hour later on its dial.
   */
  private fun addMissionForTeam(teamId: String, title: String) {
    composeTestRule.onNodeWithTag(AddMissionScreenTestTags.TITLE_FIELD).performTextInput(title)
    composeTestRule
        .onNodeWithTag(AddMissionScreenTestTags.DESCRIPTION_FIELD)
        .performTextInput("Build the stage before the first concert")
    composeTestRule
        .onNodeWithTag(AddMissionScreenTestTags.teamChip(teamId))
        .performScrollTo()
        .performClick()
    composeTestRule
        .onNodeWithTag(AddMissionScreenTestTags.START_FIELD)
        .performScrollTo()
        .performClick()
    click(AddMissionScreenTestTags.PICKER_CONFIRM)
    composeTestRule.onNodeWithTag(AddMissionScreenTestTags.END_FIELD).performClick()
    // The 24-hour picker names each hour of its dial "<hour> hours", in English.
    composeTestRule
        .onNodeWithContentDescription("${(EVENT_START_HOUR + 1) % 24} hours")
        .performClick()
    click(AddMissionScreenTestTags.PICKER_CONFIRM)
    composeTestRule
        .onNodeWithTag(AddMissionScreenTestTags.SUBMIT_BUTTON)
        .performScrollTo()
        .performClick()
  }

  /** Fills the open team form with a team called [name] and creates it. */
  private fun createTeam(name: String) {
    fun inForm(tag: String) =
        composeTestRule.onNode(
            hasTestTag(tag) and hasAnyAncestor(hasTestTag(TeamSetupScreenTestTags.ACTIVE_FORM))
        )
    inForm(CreateTeamFormTestTags.NAME_FIELD).performTextInput(name)
    inForm(CreateTeamFormTestTags.ICON_SELECTOR).performClick()
    composeTestRule.onNodeWithTag(CreateTeamFormTestTags.iconOption(TeamIcon.TRUCK)).performClick()
    inForm(CreateTeamFormTestTags.DESCRIPTION_FIELD).performTextInput("What $name does")
    inForm(CreateTeamFormTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      composeTestRule
          .onAllNodesWithTag(TeamSetupScreenTestTags.ACTIVE_FORM)
          .fetchSemanticsNodes()
          .isEmpty()
    }
  }

  private fun requestOf(eventId: String, userId: String) =
      checkNotNull(
          runBlocking {
            repositories.membershipRequestRepository.getMembershipRequest(eventId, userId)
          }
      )

  /** The text of an event card's status pill, as the screen formats [label]. */
  private fun badge(label: Int) =
      context.getString(R.string.my_events_status_pill, context.getString(label))

  /** Presses the device Back until the screen tagged [tag] shows, as a user would. */
  private fun backTo(tag: String) {
    repeat(MAX_BACK_PRESSES) {
      if (composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) return
      composeTestRule.waitForIdle()
      composeTestRule.runOnUiThread {
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
      }
      composeTestRule.waitForIdle()
    }
    waitForNode(tag)
  }

  private fun click(tag: String) {
    waitForNode(tag)
    composeTestRule.onNodeWithTag(tag).performClick()
  }

  private fun waitForNode(tag: String) {
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun textOf(node: SemanticsNodeInteraction): String =
      node.fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }

  /** The text of the node tagged [tag], or null while it is not on screen. */
  private fun textOrNull(tag: String): String? =
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()?.let { node ->
        node.config
            .getOrElseNullable(SemanticsProperties.Text) { null }
            ?.joinToString("") { it.text }
      }

  /**
   * A Google account, the same person on every sign-in: the Auth emulator maps its fixed subject to
   * the same Firebase user each time.
   */
  private class GoogleAccount(private val name: String) {
    private val subject = "google-${UUID.randomUUID()}"
    private val email = "${name.lowercase()}-${UUID.randomUUID()}@example.com"

    fun credential(): GoogleIdTokenCredential =
        GoogleIdTokenCredential.Builder()
            .setId(email)
            .setIdToken(AuthEmulator.fakeGoogleIdToken(subject, email, name))
            .build()
  }

  private companion object {
    const val TIMEOUT_MILLIS = 20_000L
    const val MAX_BACK_PRESSES = 4
    /** Hour the event starts at, which the mission's time pickers open on. */
    const val EVENT_START_HOUR = 14
  }
}
