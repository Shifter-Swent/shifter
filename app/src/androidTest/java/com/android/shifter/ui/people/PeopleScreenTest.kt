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
  private val ann = ParticipantItem("ann", "Ann Lee", isManager = true)
  private val bob = ParticipantItem("bob", "Bob Rossi", isManager = false)

  /** The Participants tab of Bar, managed by Ann, recording the confirmed changes. */
  private fun setParticipants(
      onAppointManager: (String, String) -> Unit = { _, _ -> },
      onRemoveManager: (String) -> Unit = {},
  ) {
    compose.setContent {
      MaterialTheme {
        PeopleScreen(
            uiState = MembershipRequestUIState(isLoading = false),
            onAccept = {},
            onReject = {},
            onRetry = {},
            participants =
                ParticipantsUiState(groups = listOf(ParticipantGroup(bar, listOf(ann, bob)))),
            onReloadParticipants = {},
            onAppointManager = onAppointManager,
            onRemoveManager = onRemoveManager,
            onNavigate = {},
        )
      }
    }
  }

  @Test
  fun tapMember_thenConfirm_appointsThemAndSaysWhoGoesBack() {
    var appointed: Pair<String, String>? = null
    setParticipants(onAppointManager = { teamId, userId -> appointed = teamId to userId })

    compose.onNodeWithTag(ParticipantsListTestTags.participantCard("bob")).performClick()
    compose.onNodeWithText("Make Bob Rossi manager?").assertIsDisplayed()
    compose
        .onNodeWithText("Bob Rossi will manage Bar, and Ann Lee goes back to the team.")
        .assertIsDisplayed()
    compose.onNodeWithTag(ManagerDialogTestTags.CONFIRM_BUTTON).performClick()

    compose.runOnIdle { assertEquals("bar" to "bob", appointed) }
    compose.onNodeWithTag(ManagerDialogTestTags.DIALOG).assertDoesNotExist()
  }

  @Test
  fun tapManager_thenConfirm_removesThem() {
    var removed: String? = null
    setParticipants(onRemoveManager = { removed = it })

    compose.onNodeWithTag(ParticipantsListTestTags.participantCard("ann")).performClick()
    compose.onNodeWithText("Remove Ann Lee as manager?").assertIsDisplayed()
    compose.onNodeWithText("Ann Lee goes back to the members of Bar.").assertIsDisplayed()
    compose.onNodeWithTag(ManagerDialogTestTags.CONFIRM_BUTTON).performClick()

    compose.runOnIdle { assertEquals("bar", removed) }
  }

  @Test
  fun tapParticipant_thenCancel_changesNothing() {
    var changes = 0
    setParticipants(onAppointManager = { _, _ -> changes++ }, onRemoveManager = { changes++ })

    compose.onNodeWithTag(ParticipantsListTestTags.participantCard("bob")).performClick()
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
            onNavigate = {},
        )
      }
    }
    compose.onNodeWithTag(ParticipantsListTestTags.participantCard("bob")).performClick()
    compose.onNodeWithTag(ManagerDialogTestTags.DIALOG).assertIsDisplayed()

    // A reload no longer lists Bob, e.g. because he withdrew meanwhile.
    participants = ParticipantsUiState(groups = listOf(ParticipantGroup(bar, listOf(ann))))

    compose.onNodeWithTag(ManagerDialogTestTags.DIALOG).assertDoesNotExist()
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
