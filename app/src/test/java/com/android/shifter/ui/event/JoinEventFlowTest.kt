// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter.ui.event

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequestRepositoryException
import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
import com.swent.shifter.ui.events.MyEventsTestTags
import com.swent.shifter.ui.events.MyEventsUiState
import com.swent.shifter.ui.events.staff.StaffEventsContent
import com.swent.shifter.ui.navigation.NavigationTestTags
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole join flow on the real screen and view model, with in-memory repositories: a wrong code
 * shows an error, the right one shows the event, the volunteer can go back to the code, picks a
 * team, Apply sends the request for it, and finding the event again shows that request. Once sent,
 * the request leads back to My Events with a confirmation; a failed one stays on the event.
 */
@RunWith(AndroidJUnit4::class)
class JoinEventFlowTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val sentRequests = mutableListOf<Pair<String, MembershipRequest>>()

  /** What sending a request throws, or null to send it. */
  private var applyFailure: Exception? = null

  /** The navigation of [showMyEventsThenJoin], to come back to My Events. */
  private lateinit var navController: NavHostController

  private val events =
      object : EventRepository {
        override suspend fun getEventByJoinCode(joinCode: String) = EVENT.takeIf {
          joinCode == EVENT.joinCode
        }

        override suspend fun createEvent(event: Event) = error("unused")

        override suspend fun getEvent(eventId: String) = error("unused")

        override suspend fun getEventsByOrganizer(organizerId: String) = error("unused")
      }

  private val requests =
      object : MembershipRequestRepository {
        override suspend fun apply(eventId: String, request: MembershipRequest): MembershipRequest {
          applyFailure?.let { throw it }
          return request.also { sentRequests += eventId to it }
        }

        override suspend fun accept(eventId: String, userId: String): Unit = error("unused")

        override suspend fun reject(eventId: String, userId: String): Unit = error("unused")

        override suspend fun withdraw(eventId: String, userId: String): Unit = error("unused")

        override suspend fun moveToTeam(eventId: String, userId: String, teamId: String): Unit =
            error("unused")

        override suspend fun getMembershipRequest(eventId: String, userId: String) =
            sentRequests
                .singleOrNull { (sentTo, request) -> sentTo == eventId && request.userId == userId }
                ?.second

        override suspend fun getMembershipRequestsByEId(eventId: String): List<MembershipRequest> =
            error("unused")

        override suspend fun getMembershipRequestsByUId(
            userId: String
        ): Map<String, MembershipRequest> = error("unused")
      }

  @Test
  fun joining_withWrongThenRightCode_showsAnErrorThenLetsTheVolunteerApply() {
    val teams = FakeTeamRepository(listOf(TEAM))
    val viewModel =
        JoinEventViewModel(
            events,
            requests,
            teams,
            { "volunteer-1" },
            { Instant.ofEpochSecond(600) },
        )
    composeTestRule.setContent { JoinEventScreen(viewModel, onRequestSent = {}) }

    typeCodeAndFind("WRNG23")
    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.ERROR_MESSAGE)
        .assertTextEquals("No event uses this code")
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.JOIN_CODE_FIELD).assertIsDisplayed()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON).assertDoesNotExist()

    // A code typed in lower case shows, and is found, in upper case.
    typeCodeAndFind(EVENT.joinCode.lowercase()) {
      EVENT.joinCode.forEachIndexed { index, char ->
        composeTestRule
            .onNodeWithTag(JoinEventScreenTestTags.codeBox(index), useUnmergedTree = true)
            .assertTextEquals(char.toString())
      }
    }
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.EVENT_TITLE).assertTextEquals("Lakeside")
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.ERROR_MESSAGE).assertDoesNotExist()
    composeTestRule.onNodeWithTag(EventDetailsTestTags.VENUE).assertTextEquals("Lausanne")

    // Back returns to the code step with the code kept, and the event can be found again.
    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.JOIN_CODE_FIELD)
        .assertTextContains(EVENT.joinCode)
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.FIND_BUTTON).performClick()

    // Apply waits for a team to be picked.
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.teamOption(TEAM.id)).performClick()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON).performClick()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLIED_MESSAGE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON).assertDoesNotExist()
    assertEquals(listOf(EVENT.id), sentRequests.map { it.first })
    assertEquals("volunteer-1", sentRequests.single().second.userId)
    assertEquals(TEAM.id, sentRequests.single().second.teamId)

    // Finding the event again shows the request already sent instead of offering to apply.
    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.FIND_BUTTON).performClick()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLIED_MESSAGE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON).assertDoesNotExist()
    assertEquals(1, sentRequests.size)
  }

  @Test
  fun applying_goesBackToMyEventsWithTheConfirmation() {
    showMyEventsThenJoin()

    findEventAndApply()

    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithText("Request sent to Lakeside").assertIsDisplayed()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.EVENT_TITLE).assertDoesNotExist()
    assertEquals(1, sentRequests.size)
  }

  @Test
  fun applying_whenSendingFails_staysOnTheEventWithTheError() {
    applyFailure = MembershipRequestRepositoryException.Unavailable()
    showMyEventsThenJoin()

    findEventAndApply()

    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.EVENT_TITLE).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.ERROR_MESSAGE)
        .assertTextEquals("You are offline, try again later")
    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertDoesNotExist()
    composeTestRule.onNodeWithText("Request sent to Lakeside").assertDoesNotExist()
  }

  @Test
  fun comingBackToMyEvents_afterTheConfirmation_doesNotShowItAgain() {
    showMyEventsThenJoin()
    findEventAndApply()
    composeTestRule.onNodeWithText("Request sent to Lakeside").assertIsDisplayed()

    composeTestRule.onNodeWithTag(MyEventsTestTags.SCAN_QR_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertDoesNotExist()
    composeTestRule.runOnUiThread { navController.popBackStack() }

    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithText("Request sent to Lakeside").assertDoesNotExist()
  }

  /**
   * Shows My Events, wired to the join screen as in `ShifterNavHost`, and opens the join screen
   * with its Scan QR button.
   */
  private fun showMyEventsThenJoin() {
    val viewModel =
        JoinEventViewModel(
            events,
            requests,
            FakeTeamRepository(listOf(TEAM)),
            { "volunteer-1" },
            { Instant.ofEpochSecond(600) },
        )
    composeTestRule.setContent {
      navController = rememberNavController()
      NavHost(navController, startDestination = MY_EVENTS) {
        composable(MY_EVENTS) { entry ->
          val snackbarHostState = remember { SnackbarHostState() }
          RequestSentSnackbar(entry.savedStateHandle, snackbarHostState)
          StaffEventsContent(
              state = MyEventsUiState(),
              avatarInitial = "V",
              onTabSelected = {},
              onWithdraw = {},
              onOpenEvent = {},
              onScanQrClick = { navController.navigate(JOIN) },
              onAvatarClick = {},
              snackbarHostState = snackbarHostState,
          )
        }
        composable(JOIN) {
          JoinEventScreen(viewModel) { eventTitle ->
            navController.getBackStackEntry(MY_EVENTS).savedStateHandle.putRequestSent(eventTitle)
            navController.popBackStack(MY_EVENTS, inclusive = false)
          }
        }
      }
    }
    composeTestRule.onNodeWithTag(MyEventsTestTags.SCAN_QR_BUTTON).performClick()
  }

  private fun findEventAndApply() {
    typeCodeAndFind(EVENT.joinCode)
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.teamOption(TEAM.id)).performClick()
    composeTestRule.onNodeWithTag(JoinEventScreenTestTags.APPLY_BUTTON).performClick()
  }

  /** Types [code], runs [beforeFind] to check what the screen shows, then clicks Find. */
  private fun typeCodeAndFind(code: String, beforeFind: () -> Unit = {}) {
    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.JOIN_CODE_FIELD)
        .performTextReplacement(code)
    beforeFind()
    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.FIND_BUTTON)
        .performScrollTo()
        .performClick()
  }

  private companion object {
    const val MY_EVENTS = "myEvents"
    const val JOIN = "join"
    val EVENT =
        Event(
            id = "event-1",
            organizerId = "organizer-1",
            title = "Lakeside",
            description = "A festival by the lake.",
            type = EventType.MUSIC,
            startAt = Instant.ofEpochSecond(1_000),
            endAt = Instant.ofEpochSecond(2_000),
            location = EventLocation("Lausanne"),
            joinCode = "ABC234",
            createdAt = Instant.ofEpochSecond(500),
        )
    val TEAM =
        Team(
            id = "team-a",
            eventId = EVENT.id,
            name = "Welcome",
            description = "Greet the festival-goers.",
            icon = "star",
            createdAt = Instant.ofEpochSecond(500),
        )
  }
}
