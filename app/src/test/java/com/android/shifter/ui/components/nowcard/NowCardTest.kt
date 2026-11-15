// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.nowcard

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionLocation
import com.swent.shifter.ui.checkin.NotCheckedIn
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NowCardTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val opened = mutableListOf<Mission>()
  private val contacted = mutableListOf<Mission>()

  private fun show(
      state: NowState,
      now: Instant,
      checkedInAt: Instant? = null,
      notCheckedIn: NotCheckedIn? = null,
  ) {
    composeTestRule.setContent {
      ShifterTheme {
        NowCard(
            state = state,
            now = now,
            checkedInAt = checkedInAt,
            notCheckedIn = notCheckedIn,
            onOpenMission = { opened += it },
            onContact = { contacted += it },
            zone = ZoneOffset.UTC,
        )
      }
    }
  }

  private fun assertCard(status: String, headline: String) {
    composeTestRule.onNodeWithTag(NowCardTestTags.STATUS).assertTextEquals(status)
    composeTestRule.onNodeWithTag(NowCardTestTags.HEADLINE).assertTextEquals(headline)
  }

  private fun assertCount(tag: String, count: Int) =
      composeTestRule.onAllNodesWithTag(tag).assertCountEquals(count)

  @Test
  fun later_showsTheStartWithoutActionNorBar() {
    show(NowState.Later(BAR), START.minus(Duration.ofHours(2)))

    assertCard("Next mission", "At 14:00")
    composeTestRule.onNodeWithText("Bar").assertIsDisplayed()
    composeTestRule.onNodeWithText("14:00–15:30").assertIsDisplayed()
    composeTestRule.onNodeWithText("Hall B").assertIsDisplayed()
    assertCount(NowCardTestTags.ACTION, 0)
    assertCount(NowCardTestTags.TIME_BAR, 0)
  }

  @Test
  fun soon_countsDownAndOpensTheMissionOnTheMap() {
    // 19 min 30 s left: the countdown rounds up.
    show(NowState.Soon(BAR), START.minusSeconds(19 * 60 + 30))

    assertCard("Starting soon", "In 20 min")
    composeTestRule
        .onNodeWithTag(NowCardTestTags.TIME_BAR)
        .assert(hasText("Starts 14:00", substring = true))
    composeTestRule.onNodeWithText("checks you in automatically", substring = true).assertExists()
    composeTestRule.onNodeWithTag(NowCardTestTags.ACTION).assertTextEquals("View on map")
    composeTestRule.onNodeWithTag(NowCardTestTags.ACTION).performClick()
    assertEquals(listOf(BAR), opened)
  }

  @Test
  fun soon_withoutPlace_viewsTheDetailsWithoutTheGpsHint() {
    show(NowState.Soon(NO_PLACE), START.minus(Duration.ofMinutes(20)))

    composeTestRule.onNodeWithTag(NowCardTestTags.ACTION).assertTextEquals("View details")
    composeTestRule
        .onNodeWithText("checks you in automatically", substring = true)
        .assertDoesNotExist()
  }

  @Test
  fun late_saysWhyAndTellsTheManager() {
    show(
        NowState.Late(BAR, null),
        START.plusSeconds(6 * 60),
        notCheckedIn = NotCheckedIn.OUTSIDE_ZONE,
    )

    assertCard("Not checked in", "Started 6 min ago")
    assertCount(NowCardTestTags.TIME_BAR, 1)
    composeTestRule.onNodeWithText("You're outside the mission's zone.").assertIsDisplayed()
    composeTestRule.onNodeWithTag(NowCardTestTags.ACTION).assertTextEquals("Tell your manager")
    composeTestRule.onNodeWithTag(NowCardTestTags.ACTION).performClick()
    assertEquals(listOf(BAR), contacted)
  }

  @Test
  fun late_inItsFirstMinute_startedJustNow() {
    show(NowState.Late(BAR, null), START.plusSeconds(30))

    assertCard("Not checked in", "Started just now")
  }

  @Test
  fun late_withoutPlace_saysTheManagerChecksIn() {
    show(
        NowState.Late(NO_PLACE, null),
        START.plusSeconds(6 * 60),
        notCheckedIn = NotCheckedIn.OUTSIDE_ZONE,
    )

    composeTestRule.onNodeWithText("your manager checks you in", substring = true).assertExists()
    composeTestRule.onNodeWithText("zone", substring = true).assertDoesNotExist()
  }

  @Test
  fun late_withoutKnownReason_saysNothingMore() {
    show(NowState.Late(BAR, null), START.plusSeconds(6 * 60))

    assertCount(NowCardTestTags.REASON, 0)
  }

  @Test
  fun late_eachReason_showsItsMessage() {
    val messages =
        mapOf(
            NotCheckedIn.OUTSIDE_ZONE to "You're outside the mission's zone.",
            NotCheckedIn.REFUSED to "The check-in was refused: ask your manager to check you in.",
            NotCheckedIn.OFFLINE to "Could not reach the server. Check your connection.",
            NotCheckedIn.UNEXPECTED to "Something went wrong with the automatic check-in.",
        )
    var reason by mutableStateOf(NotCheckedIn.OUTSIDE_ZONE)
    composeTestRule.setContent {
      ShifterTheme {
        NowCard(
            state = NowState.Late(BAR, null),
            now = START.plusSeconds(6 * 60),
            checkedInAt = null,
            notCheckedIn = reason,
            onOpenMission = {},
            onContact = {},
            zone = ZoneOffset.UTC,
        )
      }
    }

    messages.forEach { (value, message) ->
      reason = value
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag(NowCardTestTags.REASON).assertTextEquals(message)
    }
  }

  @Test
  fun late_ofNoTeam_tellsTheOrganizer() {
    show(NowState.Late(NO_TEAM, null), START.plusSeconds(6 * 60))

    composeTestRule.onNodeWithTag(NowCardTestTags.ACTION).assertTextEquals("Tell the organizer")
    composeTestRule.onNodeWithTag(NowCardTestTags.ACTION).performClick()
    assertEquals(listOf(NO_TEAM), contacted)
  }

  @Test
  fun late_withANextMission_showsItWithoutTakingTheAction() {
    show(NowState.Late(BAR, DESK), START.plus(Duration.ofMinutes(75)))

    composeTestRule.onNodeWithTag(NowCardTestTags.NEXT).assertIsDisplayed()
    assertCount(NowCardTestTags.ACTION, 1)
    composeTestRule.onNodeWithTag(NowCardTestTags.ACTION).assertTextEquals("Tell your manager")
  }

  @Test
  fun soon_afterAnEarlyCheckIn_saysCheckedInWithoutTheGpsHint() {
    show(
        NowState.Soon(BAR),
        START.minus(Duration.ofMinutes(5)),
        START.minus(Duration.ofMinutes(10)),
    )

    composeTestRule.onNodeWithText("Checked in at 13:50").assertIsDisplayed()
    composeTestRule
        .onNodeWithText("checks you in automatically", substring = true)
        .assertDoesNotExist()
  }

  @Test
  fun later_withoutPlace_showsTheHoursOnly() {
    show(NowState.Later(NO_PLACE), START.minus(Duration.ofHours(2)))

    composeTestRule.onNodeWithText("14:00–15:30").assertIsDisplayed()
    composeTestRule.onNodeWithText("Hall B").assertDoesNotExist()
  }

  @Test
  fun ongoing_saysWhenCheckedInAndContactsTheManager() {
    show(NowState.Ongoing(BAR, null), START.plus(Duration.ofMinutes(50)), START.plusSeconds(2 * 60))

    assertCard("Now", "40 min left")
    assertCount(NowCardTestTags.TIME_BAR, 1)
    composeTestRule.onNodeWithText("Checked in at 14:02").assertIsDisplayed()
    composeTestRule.onNodeWithTag(NowCardTestTags.ACTION).assertTextEquals("Contact your manager")
    composeTestRule.onNodeWithTag(NowCardTestTags.ACTION).performClick()
    assertEquals(listOf(BAR), contacted)
  }

  @Test
  fun ongoing_ofNoTeamWithoutCheckInTime_contactsTheOrganizer() {
    show(NowState.Ongoing(NO_TEAM, null), START.plus(Duration.ofMinutes(50)))

    composeTestRule.onNodeWithText("Checked in", substring = true).assertDoesNotExist()
    composeTestRule.onNodeWithTag(NowCardTestTags.ACTION).assertTextEquals("Contact the organizer")
  }

  @Test
  fun ongoing_withANextMission_letsTheStripTakeTheAction() {
    show(NowState.Ongoing(BAR, DESK), START.plus(Duration.ofMinutes(75)))

    assertCard("Now", "15 min left")
    composeTestRule.onNodeWithText("Then, in 30 min").assertIsDisplayed()
    assertCount(NowCardTestTags.ACTION, 1)
    composeTestRule
        .onNode(
            hasTestTag(NowCardTestTags.ACTION) and hasAnyAncestor(hasTestTag(NowCardTestTags.NEXT))
        )
        .performClick()
    assertEquals(listOf(DESK), opened)
  }

  @Test
  fun ongoing_withANextMissionWithoutPlace_viewsItsDetails() {
    val next = mission("Briefing", START.plus(Duration.ofMinutes(105)), place = null)
    show(NowState.Ongoing(BAR, next), START.plus(Duration.ofMinutes(75)))

    composeTestRule.onNodeWithTag(NowCardTestTags.ACTION).assertTextEquals("View details")
    composeTestRule.onNodeWithTag(NowCardTestTags.ACTION).performClick()
    assertEquals(listOf(next), opened)
  }

  @Test
  fun doneForToday_saysSoWithoutActionNorBar() {
    show(NowState.DoneForToday, START.plus(Duration.ofHours(3)))

    assertCard("Today", "You're done for today")
    assertCount(NowCardTestTags.ACTION, 0)
    assertCount(NowCardTestTags.TIME_BAR, 0)
  }

  @Test
  fun stateChange_keepsTheFooter() {
    var state: NowState by mutableStateOf(NowState.Soon(BAR))
    composeTestRule.setContent {
      ShifterTheme {
        NowCard(
            state = state,
            now = START.minus(Duration.ofMinutes(5)),
            checkedInAt = null,
            notCheckedIn = null,
            onOpenMission = {},
            onContact = {},
            zone = ZoneOffset.UTC,
        ) {
          var taps by remember { mutableIntStateOf(0) }
          Text("Taps $taps", Modifier.testTag(FOOTER).clickable { taps++ })
        }
      }
    }
    composeTestRule.onNodeWithTag(FOOTER).performClick()

    state = NowState.Late(BAR, null)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(FOOTER).assertTextEquals("Taps 1")
  }

  private companion object {
    const val FOOTER = "footer"
    val START: Instant = Instant.parse("2026-11-14T14:00:00Z")

    fun mission(
        title: String,
        start: Instant = START,
        place: String? = "Hall B",
        team: String? = "t",
    ) =
        Mission(
            id = title,
            eventId = "event",
            title = title,
            description = "",
            teamId = team,
            volunteersNeeded = 2,
            startAt = start,
            endAt = start.plus(Duration.ofMinutes(90)),
            location = place?.let { MissionLocation(it, GeoPosition(46.52, 6.57)) },
            createdAt = START,
        )

    val BAR = mission("Bar")
    val NO_PLACE = mission("Stage", place = null)
    val NO_TEAM = mission("Info stand", team = null)
    val DESK = mission("Welcome desk", START.plus(Duration.ofMinutes(105)), "Main gate")
  }
}
