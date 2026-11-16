// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
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
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.components.isIndeterminateSpinner
import com.swent.shifter.ui.events.staff.StaffEventsContent
import com.swent.shifter.ui.events.staff.StaffEventsScreen
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class StaffEventsContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val upcoming = SampleMyEvents.staff.filter { it.badge != EventBadge.ENDED }
  private val past = SampleMyEvents.staff.filter { it.badge == EventBadge.ENDED }

  private val withdrawn = mutableListOf<String>()
  private val opened = mutableListOf<EventCardUi>()
  private var scanQrClicks = 0
  private var avatarClicks = 0
  private var refreshes = 0

  private fun setContent(initial: MyEventsUiState) {
    composeTestRule.setContent {
      var state by remember { mutableStateOf(initial) }
      ShifterTheme {
        StaffEventsContent(
            state = state,
            avatarInitial = "J",
            onTabSelected = { state = state.copy(selectedTab = it) },
            onWithdraw = { withdrawn += it },
            onOpenEvent = { opened += it },
            onScanQrClick = { scanQrClicks++ },
            onAvatarClick = { avatarClicks++ },
            onRefresh = { refreshes++ },
        )
      }
    }
  }

  @Test
  @Config(qualifiers = "w360dp-h720dp")
  fun pull_onTheEventList_refreshes() {
    setContent(MyEventsUiState(upcoming = upcoming, past = past))

    composeTestRule.onNodeWithTag(MyEventsTestTags.EVENT_LIST).performTouchInput { swipeDown() }

    composeTestRule.runOnIdle { assertEquals(1, refreshes) }
  }

  @Test
  @Config(qualifiers = "w360dp-h720dp")
  fun pull_onTheEmptyState_refreshes() {
    setContent(MyEventsUiState())

    composeTestRule.onNodeWithTag(MyEventsTestTags.EMPTY_STATE).assertIsDisplayed()
    // From the middle of the screen, inside the message's list, to its bottom.
    composeTestRule.onNodeWithTag(MyEventsTestTags.STAFF_SCREEN).performTouchInput {
      swipeDown(startY = centerY, endY = bottom)
    }

    composeTestRule.runOnIdle { assertEquals(1, refreshes) }
  }

  @Test
  @Config(qualifiers = "w360dp-h720dp")
  fun pull_onTheScreen_reloadsTheEvents() {
    var loads = 0
    val viewModel =
        StaffEventsViewModel(
            loader = {
              loads++
              upcoming
            },
            withdrawer = {},
        )
    composeTestRule.setContent {
      ShifterTheme {
        StaffEventsScreen(
            viewModel = viewModel,
            avatarInitial = "J",
            onOpenEvent = {},
            onScanQrClick = {},
            onAvatarClick = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    assertEquals(1, loads)

    composeTestRule.onNodeWithTag(MyEventsTestTags.EVENT_LIST).performTouchInput { swipeDown() }
    composeTestRule.waitForIdle()

    composeTestRule.runOnIdle { assertEquals(2, loads) }
  }

  @Test
  fun reloading_withCards_keepsThemWithoutASpinner() {
    // An automatic reload, e.g. on coming back: only a pull shows the spinner.
    setContent(MyEventsUiState(upcoming = upcoming, isLoading = true))

    composeTestRule.onNodeWithTag(MyEventsTestTags.eventCard(upcoming.first().id)).assertExists()
    composeTestRule.onAllNodes(isIndeterminateSpinner).assertCountEquals(0)
  }

  @Test
  fun loading_withoutCards_showsOnlyTheCentredSpinner() {
    setContent(MyEventsUiState(isLoading = true))

    composeTestRule.onNodeWithTag(MyEventsTestTags.LOADING).assertIsDisplayed()
    composeTestRule.onAllNodes(isIndeterminateSpinner).assertCountEquals(1)
  }

  @Test
  @OptIn(ExperimentalTestApi::class)
  fun refreshAction_onACard_refreshes() {
    setContent(MyEventsUiState(upcoming = upcoming))

    // A card that opens is focused as a whole; one awaiting approval, by its title.
    composeTestRule
        .onNodeWithTag(MyEventsTestTags.eventCard("city-marathon"), useUnmergedTree = true)
        .performCustomAccessibilityActionWithLabel("Refresh")
    val pending = upcoming.first { it.id == "beach-cleanup" }
    composeTestRule
        .onNodeWithText(pending.title, useUnmergedTree = true)
        .performCustomAccessibilityActionWithLabel("Refresh")

    composeTestRule.runOnIdle { assertEquals(2, refreshes) }
  }

  @Test
  fun failedReload_keepsTheCardsWithTheError() {
    setContent(MyEventsUiState(upcoming = upcoming, loadFailed = true))

    composeTestRule.onNodeWithTag(MyEventsTestTags.ERROR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MyEventsTestTags.eventCard(upcoming.first().id)).assertExists()
  }

  @Test
  fun opening_showsTheHeaderAndTheUpcomingCards() {
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
  fun cards_showTheirStatusAndFooter() {
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
    val button = composeTestRule.onNodeWithTag(MyEventsTestTags.SCAN_QR_BUTTON).getBoundsInRoot()
    assertTrue(
        "the last card (bottom ${lastCard.bottom}) must end above the button (top ${button.top})",
        lastCard.bottom <= button.top,
    )
  }

  @Test
  fun withdraw_reportsTheEventIdWithoutOpeningTheEvent() {
    setContent(MyEventsUiState(upcoming = upcoming))

    composeTestRule
        .onNodeWithTag(MyEventsTestTags.eventAction("city-marathon"))
        .assertTextEquals("Withdraw")
        .performClick()

    assertEquals(listOf("city-marathon"), withdrawn)
    assertEquals(emptyList<EventCardUi>(), opened)
  }

  @Test
  fun card_ofAnAcceptedEvent_opensIt() {
    setContent(MyEventsUiState(upcoming = upcoming, past = past))

    composeTestRule.onNodeWithTag(MyEventsTestTags.eventCard("city-marathon")).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.PAST_TAB).performClick()
    // An event that is over stays readable once it is listed.
    composeTestRule.onNodeWithTag(MyEventsTestTags.eventCard(past.first().id)).performClick()

    // Each card passes its event, name included, for the top bar of its tabs.
    assertEquals(listOf(upcoming.first { it.id == "city-marathon" }, past.first()), opened)
    assertEquals(emptyList<String>(), withdrawn)
  }

  @Test
  fun card_awaitingApproval_doesNotOpenTheEvent() {
    setContent(MyEventsUiState(upcoming = upcoming))

    composeTestRule.onNodeWithTag(MyEventsTestTags.eventCard("beach-cleanup")).performClick()

    assertEquals(emptyList<EventCardUi>(), opened)
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

    composeTestRule.onNodeWithTag(MyEventsTestTags.SCAN_QR_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MyEventsTestTags.AVATAR).performClick()

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
