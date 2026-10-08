// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.events.staff.StaffEventsContent
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StaffEventsContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val upcoming = SampleMyEvents.staff.filter { it.badge != EventBadge.ENDED }
  private val past = SampleMyEvents.staff.filter { it.badge == EventBadge.ENDED }

  private val withdrawn = mutableListOf<String>()
  private var organizerViewClicks = 0
  private var scanQrClicks = 0
  private var avatarClicks = 0

  private fun setContent(initial: MyEventsUiState) {
    composeTestRule.setContent {
      var state by remember { mutableStateOf(initial) }
      ShifterTheme {
        StaffEventsContent(
            state = state,
            avatarInitial = "J",
            onTabSelected = { state = state.copy(selectedTab = it) },
            onWithdraw = { withdrawn += it },
            onOrganizerViewClick = { organizerViewClicks++ },
            onScanQrClick = { scanQrClicks++ },
            onAvatarClick = { avatarClicks++ },
        )
      }
    }
  }

  @Test
  fun showsTheHeaderAndTheUpcomingCards() {
    setContent(MyEventsUiState(upcoming = upcoming, past = past))

    composeTestRule.onNodeWithTag(MyEventsTestTags.TITLE).assertTextEquals("My Events")
    composeTestRule.onNodeWithTag(MyEventsTestTags.SUBTITLE).assertDoesNotExist()
    composeTestRule.onNodeWithTag(MyEventsTestTags.UPCOMING_TAB).assertIsSelected()
    upcoming.forEach {
      composeTestRule.onNodeWithTag(MyEventsTestTags.eventCard(it.id)).assertExists()
    }
    composeTestRule.onNodeWithTag(MyEventsTestTags.eventCard(past.first().id)).assertDoesNotExist()
  }

  @Test
  fun cardsShowTheirStatusAndFooter() {
    setContent(MyEventsUiState(upcoming = upcoming))

    composeTestRule
        .onNodeWithTag(MyEventsTestTags.statusPill("city-marathon"))
        .assertTextEquals("● Confirmed")
    composeTestRule
        .onNodeWithTag(MyEventsTestTags.statusPill("beach-cleanup"))
        .assertTextEquals("● Pending approval")
    composeTestRule.onAllNodesWithText("Role: Volunteer").assertCountEquals(2)
    composeTestRule.onNodeWithText("Awaiting organizer approval").assertIsDisplayed()
  }

  @Test
  fun lastCard_staysAboveTheFloatingButtons() {
    setContent(MyEventsUiState(upcoming = upcoming))

    composeTestRule
        .onNodeWithTag(MyEventsTestTags.EVENT_LIST)
        .performScrollToIndex(upcoming.lastIndex)

    val lastCard =
        composeTestRule
            .onNodeWithTag(MyEventsTestTags.eventCard(upcoming.last().id))
            .getBoundsInRoot()
    val button =
        composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).getBoundsInRoot()
    assertTrue(
        "the last card (bottom ${lastCard.bottom}) must end above the button (top ${button.top})",
        lastCard.bottom <= button.top,
    )
  }

  @Test
  fun withdraw_reportsTheEventId() {
    setContent(MyEventsUiState(upcoming = upcoming))

    composeTestRule
        .onNodeWithTag(MyEventsTestTags.eventAction("city-marathon"))
        .assertTextEquals("Withdraw")
        .performClick()

    assertEquals(listOf("city-marathon"), withdrawn)
  }

  @Test
  fun pastTab_showsEndedEventsWithoutWithdraw() {
    setContent(MyEventsUiState(upcoming = upcoming, past = past))

    composeTestRule.onNodeWithTag(MyEventsTestTags.PAST_TAB).performClick()

    composeTestRule.onNodeWithTag(MyEventsTestTags.PAST_TAB).assertIsSelected()
    val ended = past.first().id
    composeTestRule.onNodeWithTag(MyEventsTestTags.statusPill(ended)).assertTextEquals("● Ended")
    composeTestRule.onNodeWithTag(MyEventsTestTags.eventAction(ended)).assertDoesNotExist()
  }

  @Test
  fun floatingButtonsAndAvatar_reportClicks() {
    setContent(MyEventsUiState(upcoming = upcoming))

    composeTestRule.onNodeWithTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.SCAN_QR_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.AVATAR).performClick()

    assertEquals(1, organizerViewClicks)
    assertEquals(1, scanQrClicks)
    assertEquals(1, avatarClicks)
  }

  @Test
  fun emptyTabs_showAMessage() {
    setContent(MyEventsUiState())

    composeTestRule
        .onNodeWithTag(MyEventsTestTags.EMPTY_STATE)
        .assertTextEquals("No upcoming events")
    composeTestRule.onNodeWithTag(MyEventsTestTags.PAST_TAB).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.EMPTY_STATE).assertTextEquals("No past events")
  }
}
