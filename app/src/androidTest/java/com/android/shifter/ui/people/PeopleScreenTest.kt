// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter.ui.people

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestStatus
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
              availability =
                  listOf(AvailabilitySlot(Instant.EPOCH, Instant.EPOCH.plusSeconds(3600))),
              createdAt = Instant.EPOCH,
          ),
          "Alice Martin",
      )

  @Test
  fun tabsShowRequestsAndButtonsSendApplicantUid() {
    var accepted: String? = null
    var rejected: String? = null
    var destination: Tab? = null
    compose.setContent {
      MaterialTheme {
        PeopleScreen(
            MembershipRequestUIState(requests = listOf(item), isLoading = false),
            { accepted = it },
            { rejected = it },
            {},
            { destination = it },
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
  fun processingRequestDisablesBothActions() {
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
  fun decidedRequestsRemainVisibleWithTheirStatus() {
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
  fun emptyListAndProfileErrorHaveVisibleFeedback() {
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
  fun emptyListShowsEmptyMessage() {
    compose.setContent {
      MaterialTheme {
        MembershipRequestsScreen(MembershipRequestUIState(isLoading = false), {}, {}, {})
      }
    }
    compose.onNodeWithText("No membership requests yet.").assertIsDisplayed()
  }
}
