// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import com.swent.shifter.ui.events.organizer.OrgaEventsScreen
import com.swent.shifter.ui.events.staff.StaffEventsScreen
import com.swent.shifter.ui.theme.ShifterTheme
import io.github.kakaocup.compose.node.element.ComposeScreen
import io.github.kakaocup.compose.node.element.KNode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

class MyEventsScreen(semanticsProvider: SemanticsNodeInteractionsProvider) :
    ComposeScreen<MyEventsScreen>(semanticsProvider = semanticsProvider) {

  val staffScreen: KNode = onNode { hasTestTag(MyEventsTestTags.STAFF_SCREEN) }
  val organizerScreen: KNode = onNode { hasTestTag(MyEventsTestTags.ORGANIZER_SCREEN) }
  val subtitle: KNode = onNode { hasTestTag(MyEventsTestTags.SUBTITLE) }
  val upcomingTab: KNode = onNode { hasTestTag(MyEventsTestTags.UPCOMING_TAB) }
  val pastTab: KNode = onNode { hasTestTag(MyEventsTestTags.PAST_TAB) }
  val organizerViewButton: KNode = onNode { hasTestTag(MyEventsTestTags.ORGANIZER_VIEW_BUTTON) }
  val scanQrButton: KNode = onNode { hasTestTag(MyEventsTestTags.SCAN_QR_BUTTON) }
  val createEventButton: KNode = onNode { hasTestTag(MyEventsTestTags.CREATE_EVENT_BUTTON) }

  fun card(id: String): KNode = onNode { hasTestTag(MyEventsTestTags.eventCard(id)) }

  fun action(id: String): KNode = onNode { hasTestTag(MyEventsTestTags.eventAction(id)) }
}

/**
 * Runs both My Events screens on a device with the real [MyEventsViewModel], fed with the mock-up
 * events. Navigation is not involved: the screens only report the Organizer View click.
 */
@RunWith(AndroidJUnit4::class)
class MyEventsScreensTest : TestCase() {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun staffScreen_showsItsEventsSwitchesTabsAndReportsActions() = run {
    var organizerViewClicks = 0
    val withdrawn = mutableListOf<String>()
    val viewModel = MyEventsViewModel(SampleMyEvents.staffLoader)
    composeTestRule.setContent {
      ShifterTheme {
        StaffEventsScreen(
            viewModel = viewModel,
            avatarInitial = "J",
            onOrganizerViewClick = { organizerViewClicks++ },
            onWithdraw = { withdrawn += it },
        )
      }
    }

    ComposeScreen.onComposeScreen<MyEventsScreen>(composeTestRule) {
      step("Upcoming shows the confirmed and pending events") {
        staffScreen { assertIsDisplayed() }
        upcomingTab { assertIsSelected() }
        card("city-marathon").assertIsDisplayed()
        card("beach-cleanup").assertIsDisplayed()
        card("tech-summit").assertIsDisplayed()
        scanQrButton { assertIsDisplayed() }
      }
      step("Withdraw reports the event") {
        action("city-marathon").performClick()
        assertEquals(listOf("city-marathon"), withdrawn)
      }
      step("Past only shows ended events") {
        pastTab { performClick() }
        pastTab { assertIsSelected() }
        card("winter-food-bank").assertIsDisplayed()
        card("city-marathon").assertDoesNotExist()
      }
      step("Organizer View reports the click, navigation is left to the caller") {
        organizerViewButton { performClick() }
        assertEquals(1, organizerViewClicks)
      }
    }
  }

  @Test
  fun organizerScreen_showsItsEventsSwitchesTabsAndReportsActions() = run {
    var createClicks = 0
    val managed = mutableListOf<String>()
    val viewModel = MyEventsViewModel(SampleMyEvents.organizerLoader)
    composeTestRule.setContent {
      ShifterTheme {
        OrgaEventsScreen(
            viewModel = viewModel,
            avatarInitial = "J",
            onManageEvent = { managed += it },
            onCreateEventClick = { createClicks++ },
        )
      }
    }

    ComposeScreen.onComposeScreen<MyEventsScreen>(composeTestRule) {
      step("Upcoming shows the events in preparation and ongoing") {
        organizerScreen { assertIsDisplayed() }
        subtitle { assertTextEquals("Organizer View") }
        card("arts-festival").assertIsDisplayed()
        card("food-drive").assertIsDisplayed()
        organizerViewButton { assertDoesNotExist() }
      }
      step("Past only shows ended events") {
        pastTab { performClick() }
        card("volunteer-fair").assertIsDisplayed()
        card("arts-festival").assertDoesNotExist()
      }
      step("Manage event and Create my event report their clicks") {
        action("volunteer-fair").performClick()
        createEventButton { performClick() }
        assertEquals(listOf("volunteer-fair"), managed)
        assertEquals(1, createClicks)
      }
    }
  }
}
