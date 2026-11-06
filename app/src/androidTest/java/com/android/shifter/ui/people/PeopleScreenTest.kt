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
            onManagerChangeFailureShown = {},
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
            onManagerChangeFailureShown = {},
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
            onManagerChangeFailureShown = {},
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
            onManagerChangeFailureShown = {},
        )
      }
    }
    compose.onNodeWithTag(ParticipantsListTestTags.participantCard("bob")).performClick()
    compose.onNodeWithTag(ManagerDialogTestTags.DIALOG).assertIsDisplayed()

    // A reload no longer lists Bob, e.g. because he withdrew meanwhile.
    participants = ParticipantsUiState(groups = listOf(ParticipantGroup(bar, listOf(ann))))

    compose.onNodeWithTag(ManagerDialogTestTags.DIALOG).assertDoesNotExist()
  }

  /** The Participants tab in [state], recording what the screen reports. */
  private fun setParticipantsState(
      state: () -> ParticipantsUiState,
      onAppointManager: (String, String) -> Unit = { _, _ -> },
      onRemoveManager: (String) -> Unit = {},
      onFailureShown: () -> Unit = {},
  ) {
    compose.setContent {
      MaterialTheme {
        PeopleScreen(
            uiState = MembershipRequestUIState(isLoading = false),
            onAccept = {},
            onReject = {},
            onRetry = {},
            participants = state(),
            onReloadParticipants = {},
            onAppointManager = onAppointManager,
            onRemoveManager = onRemoveManager,
            onNavigate = {},
            onManagerChangeFailureShown = onFailureShown,
        )
      }
    }
  }

  private fun failed(managerId: String?, canRetry: Boolean) =
      ParticipantsUiState(
          groups = listOf(ParticipantGroup(bar, listOf(ann, bob))),
          managerChangeFailure = ManagerChangeFailure("bar", managerId, canRetry),
      )

  @Test
  fun failedAppointment_showsASnackbarWhoseRetryAppointsAgain() {
    var appointed: Pair<String, String>? = null
    var shown = 0
    setParticipantsState(
        state = { failed("bob", canRetry = true) },
        onAppointManager = { teamId, userId -> appointed = teamId to userId },
        onFailureShown = { shown++ },
    )

    compose.onNodeWithText("The manager could not be changed").assertIsDisplayed()
    compose.onNodeWithText("Retry").performClick()

    compose.runOnIdle {
      assertEquals("bar" to "bob", appointed)
      assertEquals(1, shown)
    }
  }

  @Test
  fun failedRemoval_showsASnackbarWhoseRetryRemovesAgain() {
    var removed: String? = null
    setParticipantsState(
        state = { failed(null, canRetry = true) },
        onRemoveManager = { removed = it },
    )

    compose.onNodeWithText("Retry").performClick()

    compose.runOnIdle { assertEquals("bar", removed) }
  }

  @Test
  fun failedChange_withoutRetrying_endsAndIsReportedAsShown() {
    var changes = 0
    var shown = 0
    setParticipantsState(
        state = { failed("bob", canRetry = true) },
        onAppointManager = { _, _ -> changes++ },
        onFailureShown = { shown++ },
    )
    compose.onNodeWithText("Retry").assertIsDisplayed()

    // The long duration of a Snackbar: with the default of an action, it would never end.
    compose.mainClock.advanceTimeBy(11_000)

    compose.runOnIdle {
      assertEquals(1, shown)
      assertEquals(0, changes)
    }
  }

  @Test
  fun failedChange_thatCannotBeRetried_saysSoWithoutARetry() {
    var shown = 0
    setParticipantsState(
        state = { failed("bob", canRetry = false) },
        onFailureShown = { shown++ },
    )

    compose.onNodeWithText("This change is no longer possible").assertIsDisplayed()
    compose.onNodeWithText("Retry").assertDoesNotExist()

    compose.mainClock.advanceTimeBy(5_000)
    compose.runOnIdle { assertEquals(1, shown) }
  }

  @Test
  fun failedChange_withTheListScrolled_showsTheSnackbar() {
    val many = (1..40).map { ParticipantItem("user$it", "User $it", isManager = false) }
    val loaded = ParticipantsUiState(groups = listOf(ParticipantGroup(bar, many)))
    var participants by mutableStateOf(loaded)
    setParticipantsState(state = { participants })
    compose
        .onNode(hasScrollToNodeAction())
        .performScrollToNode(hasTestTag(ParticipantsListTestTags.participantCard("user40")))
    compose.onNodeWithTag(ParticipantsListTestTags.participantCard("user1")).assertDoesNotExist()

    participants = loaded.copy(managerChangeFailure = ManagerChangeFailure("bar", "user40", true))

    compose.onNodeWithText("The manager could not be changed").assertIsDisplayed()
    compose.onNodeWithTag(ParticipantsListTestTags.participantCard("user40")).assertIsDisplayed()
  }

  @Test
  fun updating_showsTheProgressOnTheParticipantsTabOnly() {
    val updating =
        ParticipantsUiState(
            groups = listOf(ParticipantGroup(bar, listOf(ann, bob))),
            isUpdating = true,
        )
    setParticipantsState(state = { updating })

    compose
        .onNodeWithTag(PeopleScreenTestTags.MANAGER_UPDATE_PROGRESS)
        .assertIsDisplayed()
        .assertContentDescriptionEquals("Changing the manager")

    compose.onNodeWithTag(PeopleScreenTestTags.REQUESTS_TAB).performClick()

    compose.onNodeWithTag(PeopleScreenTestTags.MANAGER_UPDATE_PROGRESS).assertDoesNotExist()
  }

  @Test
  fun notUpdating_showsNoProgress() {
    setParticipants()

    compose.onNodeWithTag(PeopleScreenTestTags.MANAGER_UPDATE_PROGRESS).assertDoesNotExist()
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
