// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.people

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.team.Team
import com.swent.shifter.ui.membership.MembershipRequestItemUIState
import com.swent.shifter.ui.membership.MembershipRequestUIState
import com.swent.shifter.ui.membership.MembershipRequestsScreen
import com.swent.shifter.ui.navigation.NavigationTestTags
import com.swent.shifter.ui.navigation.OrganizerTabs
import com.swent.shifter.ui.navigation.Tab
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PeopleScreenTest {
  @get:Rule val compose = createComposeRule()
  private val item =
      MembershipRequestItemUIState(
          MembershipRequest(
              id = "alice",
              userId = "alice",
              teamId = "bar",
              availability =
                  listOf(AvailabilitySlot(Instant.EPOCH, Instant.EPOCH.plusSeconds(3600))),
              createdAt = Instant.EPOCH,
          ),
          "Alice Martin",
      )

  @Test
  fun tabs_showRequestsAndButtonsSendApplicantUid() {
    var accepted: String? = null
    var rejected: String? = null
    var destination: Tab? = null
    compose.setContent {
      MaterialTheme {
        PeopleScreen(
            uiState = MembershipRequestUIState(requests = listOf(item), isLoading = false),
            onAccept = { accepted = it },
            onReject = { rejected = it },
            onRetry = {},
            participants = ParticipantsUiState(),
            onReloadParticipants = {},
            onAppointManager = { _, _ -> },
            onRemoveManager = {},
            onMoveToTeam = { _, _ -> },
            eventTitle = "City Marathon",
            onBack = {},
            onNavigate = { destination = it },
        )
      }
    }
    compose.onNodeWithText("Participants").assertIsSelected()
    compose.onNodeWithText("Requests").performClick()
    compose.onNodeWithText("Alice Martin").assertIsDisplayed()
    compose.onNodeWithText("Accept").performClick()
    compose.onNodeWithText("Reject").performClick()
    compose.runOnIdle {
      assertEquals("alice", accepted)
      assertEquals("alice", rejected)
    }
    compose.onNodeWithText("Overview").performClick()
    compose.runOnIdle { assertEquals(OrganizerTabs.Overview, destination) }
    compose.onNodeWithText("Participants").performClick()
    compose.onNodeWithText("Alice Martin").assertDoesNotExist()
  }

  @Test
  fun participantsTab_showsTheParticipantsAndReloadsThemOnlyWhenComingBack() {
    var reloads = 0
    compose.setContent {
      MaterialTheme {
        PeopleScreen(
            uiState = MembershipRequestUIState(isLoading = false),
            onAccept = {},
            onReject = {},
            onRetry = {},
            participants =
                ParticipantsUiState(
                    groups =
                        listOf(ParticipantGroup(null, listOf(ParticipantItem("bob", "Bob", false))))
                ),
            onReloadParticipants = { reloads++ },
            onAppointManager = { _, _ -> },
            onRemoveManager = {},
            onMoveToTeam = { _, _ -> },
            eventTitle = "City Marathon",
            onBack = {},
            onNavigate = {},
        )
      }
    }
    compose.onNodeWithTag(ParticipantsListTestTags.participantCard("bob")).assertIsDisplayed()
    compose.onNodeWithText("Participants").performClick()
    compose.runOnIdle { assertEquals(0, reloads) }

    compose.onNodeWithText("Requests").performClick()
    compose.onNodeWithText("Participants").performClick()

    compose.runOnIdle { assertEquals(1, reloads) }
    compose.onNodeWithTag(ParticipantsListTestTags.participantCard("bob")).assertIsDisplayed()
  }

  private val bar =
      Team(
          id = "bar",
          eventId = "event",
          name = "Bar",
          description = "Serve drinks",
          icon = "wine",
          managerId = "ann",
          memberIds = listOf("bob"),
          createdAt = Instant.EPOCH,
      )
  private val kitchen =
      bar.copy(id = "kitchen", name = "Kitchen", managerId = null, memberIds = emptyList())
  private val cat = ParticipantItem("cat", "Cat Diaz", isManager = false)
  private val ann = ParticipantItem("ann", "Ann Lee", isManager = true)
  private val bob = ParticipantItem("bob", "Bob Rossi", isManager = false)

  /** The Participants tab of Bar, managed by Ann, recording the confirmed changes. */
  private fun setParticipants(
      onAppointManager: (String, String) -> Unit = { _, _ -> },
      onRemoveManager: (String) -> Unit = {},
      onMoveToTeam: (String, String) -> Unit = { _, _ -> },
  ) {
    compose.setContent {
      MaterialTheme {
        PeopleScreen(
            uiState = MembershipRequestUIState(isLoading = false),
            onAccept = {},
            onReject = {},
            onRetry = {},
            participants =
                ParticipantsUiState(
                    groups =
                        listOf(
                            ParticipantGroup(bar, listOf(ann, bob)),
                            ParticipantGroup(kitchen, emptyList()),
                            ParticipantGroup(null, listOf(cat)),
                        )
                ),
            onReloadParticipants = {},
            onAppointManager = onAppointManager,
            onRemoveManager = onRemoveManager,
            onMoveToTeam = onMoveToTeam,
            eventTitle = "City Marathon",
            onBack = {},
            onNavigate = {},
        )
      }
    }
  }

  /** Unfolds the card of [userId] and taps its manager action, as the organizer does. */
  private fun unfoldAndAct(userId: String) {
    compose.onNodeWithTag(ParticipantsListTestTags.participantCard(userId)).performClick()
    compose.onNodeWithTag(ParticipantsListTestTags.managerButton(userId)).performClick()
  }

  @Test
  fun changeTeam_thenPickATeam_movesTheVolunteerThere() {
    var moved: Pair<String, String>? = null
    setParticipants(onMoveToTeam = { userId, teamId -> moved = userId to teamId })

    compose.onNodeWithTag(ParticipantsListTestTags.participantCard("bob")).performClick()
    compose.onNodeWithTag(ParticipantsListTestTags.moveButton("bob")).performClick()
    compose.onNodeWithText("Move Bob Rossi").assertIsDisplayed()
    compose.onNodeWithText("Bob Rossi leaves Bar and its next missions.").assertIsDisplayed()
    // Bar is the team he is in, so only Kitchen is offered.
    compose.onNodeWithTag(MoveDialogTestTags.teamOption("bar")).assertDoesNotExist()
    compose.onNodeWithTag(MoveDialogTestTags.CONFIRM_BUTTON).assertIsNotEnabled()
    compose.onNodeWithTag(MoveDialogTestTags.teamOption("kitchen")).performClick()
    compose.onNodeWithTag(MoveDialogTestTags.CONFIRM_BUTTON).performClick()

    compose.runOnIdle { assertEquals("bob" to "kitchen", moved) }
    compose.onNodeWithTag(MoveDialogTestTags.DIALOG).assertDoesNotExist()
  }

  @Test
  fun addToATeam_ofAVolunteerWithoutTeam_offersEveryTeam() {
    var moved: Pair<String, String>? = null
    setParticipants(onMoveToTeam = { userId, teamId -> moved = userId to teamId })

    compose.onNodeWithTag(ParticipantsListTestTags.participantCard("cat")).performScrollTo()
    compose.onNodeWithTag(ParticipantsListTestTags.participantCard("cat")).performClick()
    compose.onNodeWithTag(ParticipantsListTestTags.moveButton("cat")).performClick()
    compose.onNodeWithText("Add Cat Diaz to a team").assertIsDisplayed()
    compose.onNodeWithTag(MoveDialogTestTags.teamOption("kitchen")).assertIsDisplayed()
    compose.onNodeWithTag(MoveDialogTestTags.teamOption("bar")).performClick()
    compose.onNodeWithTag(MoveDialogTestTags.CONFIRM_BUTTON).performClick()

    compose.runOnIdle { assertEquals("cat" to "bar", moved) }
  }

  @Test
  fun changeTeam_thenCancel_movesNobody() {
    var moves = 0
    setParticipants(onMoveToTeam = { _, _ -> moves++ })

    compose.onNodeWithTag(ParticipantsListTestTags.participantCard("bob")).performClick()
    compose.onNodeWithTag(ParticipantsListTestTags.moveButton("bob")).performClick()
    compose.onNodeWithTag(MoveDialogTestTags.CANCEL_BUTTON).performClick()

    compose.onNodeWithTag(MoveDialogTestTags.DIALOG).assertDoesNotExist()
    compose.runOnIdle { assertEquals(0, moves) }
  }

  @Test
  fun makeManager_thenConfirm_appointsThemAndSaysWhoGoesBack() {
    var appointed: Pair<String, String>? = null
    setParticipants(onAppointManager = { teamId, userId -> appointed = teamId to userId })

    unfoldAndAct("bob")
    compose.onNodeWithText("Make Bob Rossi manager?").assertIsDisplayed()
    compose
        .onNodeWithText("Bob Rossi will manage Bar, and Ann Lee goes back to the team.")
        .assertIsDisplayed()
    compose.onNodeWithTag(ManagerDialogTestTags.CONFIRM_BUTTON).performClick()

    compose.runOnIdle { assertEquals("bar" to "bob", appointed) }
    compose.onNodeWithTag(ManagerDialogTestTags.DIALOG).assertDoesNotExist()
  }

  @Test
  fun stopManaging_thenConfirm_removesThem() {
    var removed: String? = null
    setParticipants(onRemoveManager = { removed = it })

    unfoldAndAct("ann")
    compose.onNodeWithText("Remove Ann Lee as manager?").assertIsDisplayed()
    compose.onNodeWithText("Ann Lee goes back to the members of Bar.").assertIsDisplayed()
    compose.onNodeWithTag(ManagerDialogTestTags.CONFIRM_BUTTON).performClick()

    compose.runOnIdle { assertEquals("bar", removed) }
  }

  @Test
  fun makeManager_thenCancel_changesNothing() {
    var changes = 0
    setParticipants(onAppointManager = { _, _ -> changes++ }, onRemoveManager = { changes++ })

    unfoldAndAct("bob")
    compose.onNodeWithTag(ManagerDialogTestTags.CANCEL_BUTTON).performClick()

    compose.onNodeWithTag(ManagerDialogTestTags.DIALOG).assertDoesNotExist()
    compose.runOnIdle { assertEquals(0, changes) }
  }

  @Test
  fun participantGoneFromTheList_closesTheDialog() {
    var participants by
        mutableStateOf(
            ParticipantsUiState(groups = listOf(ParticipantGroup(bar, listOf(ann, bob))))
        )
    compose.setContent {
      MaterialTheme {
        PeopleScreen(
            uiState = MembershipRequestUIState(isLoading = false),
            onAccept = {},
            onReject = {},
            onRetry = {},
            participants = participants,
            onReloadParticipants = {},
            onAppointManager = { _, _ -> },
            onRemoveManager = {},
            onMoveToTeam = { _, _ -> },
            eventTitle = "City Marathon",
            onBack = {},
            onNavigate = {},
        )
      }
    }
    unfoldAndAct("bob")
    compose.onNodeWithTag(ManagerDialogTestTags.DIALOG).assertIsDisplayed()

    // A reload no longer lists Bob, e.g. because he withdrew meanwhile.
    participants = ParticipantsUiState(groups = listOf(ParticipantGroup(bar, listOf(ann))))

    compose.onNodeWithTag(ManagerDialogTestTags.DIALOG).assertDoesNotExist()
  }

  @Test
  fun participantGoneFromTheList_closesTheMoveDialogForGood() {
    val kitchenGroup = ParticipantGroup(kitchen, emptyList())
    val withBob =
        ParticipantsUiState(groups = listOf(ParticipantGroup(bar, listOf(ann, bob)), kitchenGroup))
    var participants by mutableStateOf(withBob)
    compose.setContent {
      MaterialTheme {
        PeopleScreen(
            uiState = MembershipRequestUIState(isLoading = false),
            onAccept = {},
            onReject = {},
            onRetry = {},
            participants = participants,
            onReloadParticipants = {},
            onAppointManager = { _, _ -> },
            onRemoveManager = {},
            onMoveToTeam = { _, _ -> },
            eventTitle = "City Marathon",
            onBack = {},
            onNavigate = {},
        )
      }
    }
    compose.onNodeWithTag(ParticipantsListTestTags.participantCard("bob")).performClick()
    compose.onNodeWithTag(ParticipantsListTestTags.moveButton("bob")).performClick()
    compose.onNodeWithTag(MoveDialogTestTags.DIALOG).assertIsDisplayed()

    // A reload no longer lists Bob, e.g. because he withdrew meanwhile.
    participants =
        ParticipantsUiState(groups = listOf(ParticipantGroup(bar, listOf(ann)), kitchenGroup))
    compose.onNodeWithTag(MoveDialogTestTags.DIALOG).assertDoesNotExist()

    // Listed again later, he does not bring the dialog back on his own.
    participants = withBob
    compose.onNodeWithTag(MoveDialogTestTags.DIALOG).assertDoesNotExist()
  }

  @Test
  fun participantMadeManager_closesTheMoveDialog() {
    val kitchenGroup = ParticipantGroup(kitchen, emptyList())
    var participants by
        mutableStateOf(
            ParticipantsUiState(
                groups = listOf(ParticipantGroup(bar, listOf(ann, bob)), kitchenGroup)
            )
        )
    var moves = 0
    compose.setContent {
      MaterialTheme {
        PeopleScreen(
            uiState = MembershipRequestUIState(isLoading = false),
            onAccept = {},
            onReject = {},
            onRetry = {},
            participants = participants,
            onReloadParticipants = {},
            onAppointManager = { _, _ -> },
            onRemoveManager = {},
            onMoveToTeam = { _, _ -> moves++ },
            eventTitle = "City Marathon",
            onBack = {},
            onNavigate = {},
        )
      }
    }
    compose.onNodeWithTag(ParticipantsListTestTags.participantCard("bob")).performClick()
    compose.onNodeWithTag(ParticipantsListTestTags.moveButton("bob")).performClick()
    compose.onNodeWithTag(MoveDialogTestTags.DIALOG).assertIsDisplayed()

    // A reload shows Bob as the manager of Bar now: a manager is never moved.
    participants =
        ParticipantsUiState(
            groups =
                listOf(
                    ParticipantGroup(
                        bar,
                        listOf(bob.copy(isManager = true), ann.copy(isManager = false)),
                    ),
                    kitchenGroup,
                )
        )
    compose.onNodeWithTag(MoveDialogTestTags.DIALOG).assertDoesNotExist()
    compose.runOnIdle { assertEquals(0, moves) }
  }

  @Test
  fun topBar_showsTheEventNameAndBackCallsOnBack() {
    var backs = 0
    compose.setContent {
      MaterialTheme {
        PeopleScreen(
            uiState = MembershipRequestUIState(isLoading = false),
            onAccept = {},
            onReject = {},
            onRetry = {},
            participants = ParticipantsUiState(),
            onReloadParticipants = {},
            onAppointManager = { _, _ -> },
            onRemoveManager = {},
            onMoveToTeam = { _, _ -> },
            eventTitle = "City Marathon",
            onBack = { backs++ },
            onNavigate = {},
        )
      }
    }

    compose.onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE).assertTextEquals("City Marathon")
    compose.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()

    assertEquals(1, backs)
  }

  @Test
  fun processingRequest_disablesBothActions() {
    compose.setContent {
      MaterialTheme {
        MembershipRequestsScreen(
            MembershipRequestUIState(
                requests = listOf(item),
                isLoading = false,
                processingRequestIds = setOf("alice"),
            ),
            {},
            {},
            {},
        )
      }
    }
    compose.onNodeWithText("Accept").assertIsNotEnabled()
    compose.onNodeWithText("Reject").assertIsNotEnabled()
  }

  @Test
  fun decidedRequests_remainVisibleWithTheirStatus() {
    compose.setContent {
      MaterialTheme {
        MembershipRequestsScreen(
            MembershipRequestUIState(
                requests =
                    listOf(
                        item.copy(
                            request = item.request.copy(status = MembershipRequestStatus.REJECTED)
                        ),
                        item.copy(
                            request =
                                item.request.copy(
                                    id = "bob",
                                    userId = "bob",
                                    status = MembershipRequestStatus.ACCEPTED,
                                ),
                            displayName = "Bob Smith",
                        ),
                    ),
                isLoading = false,
            ),
            {},
            {},
            {},
        )
      }
    }
    compose.onNodeWithText("Alice Martin").assertIsDisplayed()
    compose.onNodeWithText("Rejected").assertIsDisplayed()
    compose.onNodeWithText("Bob Smith").assertIsDisplayed()
    compose.onNodeWithText("Accepted").assertIsDisplayed()
    compose.onNodeWithText("Accept").assertDoesNotExist()
    compose.onNodeWithText("Reject").assertDoesNotExist()
  }

  @Test
  fun emptyListAndProfileError_haveVisibleFeedback() {
    var retried = false
    compose.setContent {
      MaterialTheme {
        MembershipRequestsScreen(
            MembershipRequestUIState(isLoading = false, errorMsg = "Names unavailable"),
            {},
            {},
            { retried = true },
        )
      }
    }
    compose.onNodeWithText("Names unavailable").assertIsDisplayed()
    compose.onNodeWithText("Retry").performClick()
    compose.runOnIdle { assertEquals(true, retried) }
  }

  @Test
  fun emptyList_showsEmptyMessage() {
    compose.setContent {
      MaterialTheme {
        MembershipRequestsScreen(MembershipRequestUIState(isLoading = false), {}, {}, {})
      }
    }
    compose.onNodeWithText("No membership requests yet.").assertIsDisplayed()
  }
}
