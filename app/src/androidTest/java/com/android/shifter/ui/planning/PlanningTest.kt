// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.planning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.components.days.DayHeaderTestTags
import com.swent.shifter.ui.components.days.DayStripTestTags
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanningTest {

  @get:Rule val compose = createComposeRule()

  private fun at(time: String, date: LocalDate = SATURDAY): Instant =
      Instant.parse("${date}T$time:00Z")

  private fun mission(
      id: String,
      start: String = "08:00",
      end: String = "09:00",
      title: String = "Mission $id",
      address: String? = null,
      checkedInAt: Instant? = null,
      date: LocalDate = SATURDAY,
  ) = PlanningMission(id, title, at(start, date), at(end, date), address, checkedInAt)

  @Composable
  private fun PlanningUnderTest(
      missions: List<PlanningMission>,
      eventPeriod: ClosedRange<Instant>?,
      today: LocalDate,
  ) {
    // In UTC, so the days and hours do not depend on the time zone of the device running the tests.
    ShifterTheme { Planning(missions, eventPeriod, today, zone = ZoneOffset.UTC) }
  }

  private fun show(
      missions: List<PlanningMission>,
      eventPeriod: ClosedRange<Instant>? = null,
      today: LocalDate = SATURDAY,
  ) {
    compose.setContent { PlanningUnderTest(missions, eventPeriod, today) }
  }

  private fun missionShown(id: String) =
      compose.onNodeWithTag(PlanningTestTags.mission(id)).assertIsDisplayed()

  private fun missionHidden(id: String) =
      compose.onNodeWithTag(PlanningTestTags.mission(id)).assertDoesNotExist()

  private fun pick(day: LocalDate) = compose.onNodeWithTag(DayStripTestTags.day(day)).performClick()

  private fun swipeLeftOn(tag: String) =
      compose.onNodeWithTag(tag).performTouchInput { swipeLeft() }

  private fun swipeRightOn(tag: String) =
      compose.onNodeWithTag(tag).performTouchInput { swipeRight() }

  private fun daySelected(day: LocalDate) =
      compose.onNodeWithTag(DayStripTestTags.day(day)).assertIsSelected()

  /** Friday 13 to Monday 16, with a mission on Saturday and one on Sunday. */
  private val festival = at("08:00", FRIDAY)..at("22:00", MONDAY)
  private val weekend = listOf(mission("sat", date = SATURDAY), mission("sun", date = SUNDAY))

  private fun topOf(missionId: String): Float =
      compose
          .onNodeWithTag(PlanningTestTags.mission(missionId))
          .fetchSemanticsNode()
          .boundsInRoot
          .top

  private fun leftOf(missionId: String): Float =
      compose
          .onNodeWithTag(PlanningTestTags.mission(missionId))
          .fetchSemanticsNode()
          .boundsInRoot
          .left

  @Test
  fun planning_withUnsortedMissions_showsThemChronologically() {
    show(listOf(mission("late", start = "10:00", end = "11:00"), mission("early", start = "08:00")))

    // The ties are covered by PlanningOrderTest: this only checks that the rows follow the order.
    assertTrue(topOf("early") < topOf("late"))
  }

  @Test
  fun planning_withoutMissions_showsTheEmptyState() {
    show(emptyList())

    compose.onNodeWithTag(PlanningTestTags.EMPTY).assertIsDisplayed()
  }

  @Test
  fun planning_withMissions_hidesTheEmptyState() {
    show(listOf(mission("m1")))

    compose.onNodeWithTag(PlanningTestTags.EMPTY).assertDoesNotExist()
  }

  @Test
  fun missionRow_showsItsTitleAndHours() {
    show(listOf(mission("m1", start = "08:30", end = "11:00", title = "Hand out water")))

    compose.onNodeWithText("Hand out water").assertIsDisplayed()
    compose.onNodeWithText("08:30–11:00").assertIsDisplayed()
  }

  @Test
  fun missionRow_withLocation_showsTheAddress() {
    show(listOf(mission("m1", address = "Hydration station A")))

    compose.onNodeWithTag(PlanningTestTags.address("m1")).assertTextEquals("Hydration station A")
  }

  @Test
  fun missionRow_withoutLocation_hidesTheLocation() {
    show(listOf(mission("m1")))

    compose.onNodeWithTag(PlanningTestTags.mission("m1")).assertIsDisplayed()
    compose.onNodeWithTag(PlanningTestTags.address("m1")).assertDoesNotExist()
  }

  @Test
  fun missionRow_checkedIn_showsTheCheckInTime() {
    show(listOf(mission("m1", address = "Station A", checkedInAt = at("08:31"))))

    compose.onNodeWithTag(PlanningTestTags.checkIn("m1")).assertTextEquals("Checked in at 08:31")
  }

  @Test
  fun missionRow_notCheckedIn_hidesTheCheckInStatus() {
    show(listOf(mission("m1", address = "Station A")))

    compose.onNodeWithTag(PlanningTestTags.checkIn("m1")).assertDoesNotExist()
  }

  @Test
  fun severalDays_showsTheStripAndOnlyTodaysMissions() {
    show(weekend, festival)

    compose.onNodeWithTag(DayStripTestTags.STRIP).assertIsDisplayed()
    missionShown("sat")
    missionHidden("sun")
    compose.onNodeWithTag(DayHeaderTestTags.BACK_TO_TODAY).assertDoesNotExist()
  }

  @Test
  fun beforeTheEvent_opensItsFirstDay() {
    show(weekend, festival, today = LocalDate.parse("2025-06-01"))

    compose
        .onNodeWithTag(PlanningTestTags.EMPTY_DAY)
        .assertTextEquals("Nothing planned on Friday 13 June")
    compose.onNodeWithTag(DayHeaderTestTags.BACK_TO_TODAY).assertDoesNotExist()
  }

  @Test
  fun afterTheEvent_opensItsLastDay() {
    show(weekend, eventPeriod = null, today = LocalDate.parse("2025-06-30"))

    missionShown("sun")
    missionHidden("sat")
  }

  @Test
  fun pickingADay_showsItsMissionsAndBackToToday() {
    show(weekend, festival)

    pick(SUNDAY)

    missionShown("sun")
    missionHidden("sat")
    compose.onNodeWithTag(DayHeaderTestTags.BACK_TO_TODAY).assertIsDisplayed()
  }

  @Test
  fun backToToday_showsTodayAgain() {
    show(weekend, festival)
    pick(SUNDAY)

    compose.onNodeWithTag(DayHeaderTestTags.BACK_TO_TODAY).performClick()

    missionShown("sat")
    compose.onNodeWithTag(DayHeaderTestTags.BACK_TO_TODAY).assertDoesNotExist()
  }

  @Test
  fun swipingLeft_showsTheNextDay() {
    show(weekend, festival)

    swipeLeftOn(PlanningTestTags.mission("sat"))

    missionShown("sun")
    missionHidden("sat")
    daySelected(SUNDAY)
    compose.onNodeWithTag(DayHeaderTestTags.BACK_TO_TODAY).assertIsDisplayed()
  }

  @Test
  fun swipingRight_showsThePreviousDay() {
    show(weekend, festival)

    swipeRightOn(PlanningTestTags.mission("sat"))

    compose
        .onNodeWithTag(PlanningTestTags.EMPTY_DAY)
        .assertTextEquals("Nothing planned on Friday 13 June")
    daySelected(FRIDAY)
  }

  @Test
  fun aShortSwipe_staysOnTheDay() {
    show(weekend, festival)

    // A quarter of the row, slowly: less than the half that changes the day, and no flick.
    compose.onNodeWithTag(PlanningTestTags.mission("sat")).performTouchInput {
      swipeLeft(startX = centerX, endX = centerX - width / 4f, durationMillis = 2_000)
    }

    missionShown("sat")
    daySelected(SATURDAY)
  }

  @Test
  fun aShortFlick_showsTheNextDay() {
    show(weekend, festival)

    // A quarter of the row, but twice as fast as a flick, in pixels on any screen.
    compose.onNodeWithTag(PlanningTestTags.mission("sat")).performTouchInput {
      swipeWithVelocity(
          start = center,
          end = center - Offset(width / 4f, 0f),
          endVelocity = 800.dp.toPx(),
      )
    }

    missionShown("sun")
    daySelected(SUNDAY)
  }

  @Test
  fun pastHalfTheWidth_theDayLeavesFromUnderTheFinger() {
    show(weekend, festival)
    val start = leftOf("sat")
    val row = compose.onNodeWithTag(PlanningTestTags.mission("sat"))
    val width = row.fetchSemanticsNode().size.width
    compose.mainClock.autoAdvance = false

    // Seven tenths of the row, then held still so it is no flick.
    row.performTouchInput {
      down(center)
      repeat(7) { moveBy(Offset(-width / 10f, 0f)) }
      advanceEventTime(200)
      up()
    }
    compose.mainClock.advanceTimeByFrame()

    // Sunday is on its way, and Saturday leaves from where it was let go, not from its place.
    assertTrue(leftOf("sat") < start - width / 4f)
  }

  @Test
  fun caughtAgainWhileGoingBack_staysUnderTheFinger() {
    show(weekend, festival)
    val start = leftOf("sat")
    val row = compose.onNodeWithTag(PlanningTestTags.mission("sat"))
    val width = row.fetchSemanticsNode().size.width
    compose.mainClock.autoAdvance = false

    // A third of the row, let go: it goes back to its place.
    row.performTouchInput {
      down(center)
      repeat(3) { moveBy(Offset(-width / 10f, 0f)) }
      advanceEventTime(200)
      up()
    }
    compose.mainClock.advanceTimeBy(50)
    // Caught again on its way, and held.
    row.performTouchInput {
      down(center)
      repeat(2) { moveBy(Offset(-width / 10f, 0f)) }
    }
    compose.mainClock.advanceTimeBy(1_000)

    // The animation stopped: it stays under the finger instead of going back.
    assertTrue(leftOf("sat") < start - width / 10f)
    row.performTouchInput { up() }
  }

  @Test
  fun rightToLeft_theNextDayComesFromTheLeft() {
    compose.setContent {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        PlanningUnderTest(weekend, festival, SATURDAY)
      }
    }
    compose.mainClock.autoAdvance = false

    // Right to left, swiping right shows the next day.
    swipeRightOn(PlanningTestTags.mission("sat"))
    compose.mainClock.advanceTimeBy(100)

    // A few frames into the slide, both days are on screen, the next one on the finger's side.
    assertTrue(leftOf("sun") < leftOf("sat"))
  }

  @Test
  fun tappingTodayInTheStrip_followsTodayAgain() {
    var today by mutableStateOf(SATURDAY)
    compose.setContent { PlanningUnderTest(weekend, festival, today) }
    pick(SUNDAY)

    pick(SATURDAY)
    // The night goes past 04:00.
    today = SUNDAY

    daySelected(SUNDAY)
  }

  @Test
  fun swipingBackToToday_hidesBackToToday() {
    show(weekend, festival)
    pick(SUNDAY)

    swipeRightOn(PlanningTestTags.mission("sun"))

    missionShown("sat")
    compose.onNodeWithTag(DayHeaderTestTags.BACK_TO_TODAY).assertDoesNotExist()
  }

  @Test
  fun swiping_stopsAtTheEndsOfTheEvent() {
    show(weekend, festival)
    pick(MONDAY)

    swipeLeftOn(PlanningTestTags.EMPTY_DAY)
    daySelected(MONDAY)

    pick(FRIDAY)
    swipeRightOn(PlanningTestTags.EMPTY_DAY)
    daySelected(FRIDAY)
  }

  @Test
  fun aDayWithoutMissions_saysNothingIsPlanned() {
    show(weekend, festival)

    pick(MONDAY)

    compose
        .onNodeWithTag(PlanningTestTags.EMPTY_DAY)
        .assertTextEquals("Nothing planned on Monday 16 June")
  }

  @Test
  fun withoutTheEventDates_offersTheDaysBetweenTheMissions() {
    show(listOf(mission("sat", date = SATURDAY), mission("mon", date = MONDAY)))

    compose.onNodeWithTag(DayStripTestTags.day(SUNDAY)).assertIsDisplayed()
    compose.onNodeWithTag(DayStripTestTags.day(FRIDAY)).assertDoesNotExist()
  }

  @Test
  fun aNightShift_isShownWithTheEveningBefore_withItsDay() {
    show(
        listOf(
            mission("night", start = "02:00", end = "04:00", date = SUNDAY),
            mission("breakfast", start = "08:00", date = SUNDAY),
        ),
        festival,
    )

    missionShown("night")
    missionHidden("breakfast")
    compose.onNodeWithText("Sun 02:00–04:00").assertIsDisplayed()
  }

  @Test
  fun aOneDayEvent_hasNoStrip() {
    show(weekend.take(1), eventPeriod = at("08:00")..at("22:00"))

    compose.onNodeWithTag(DayStripTestTags.STRIP).assertDoesNotExist()
    missionShown("sat")
  }

  @Test
  fun aOneDayEvent_onAnotherDay_datesItsMissions() {
    // The day before, with no strip to say the mission is not today.
    show(weekend.take(1), eventPeriod = at("08:00")..at("22:00"), today = FRIDAY)

    compose.onNodeWithText("14 Jun, 08:00–09:00").assertIsDisplayed()
  }

  @Test
  fun aOneDayEvent_onItsDay_leavesTheDateOut() {
    show(weekend.take(1), eventPeriod = at("08:00")..at("22:00"))

    compose.onNodeWithText("08:00–09:00").assertIsDisplayed()
  }

  @Test
  fun withoutMissions_hasNoStrip() {
    show(emptyList(), festival)

    compose.onNodeWithTag(DayStripTestTags.STRIP).assertDoesNotExist()
    compose.onNodeWithTag(PlanningTestTags.EMPTY).assertIsDisplayed()
  }

  @Test
  fun thePickedDay_survivesARecreation() {
    val restoration = StateRestorationTester(compose)
    restoration.setContent { PlanningUnderTest(weekend, festival, SATURDAY) }
    pick(SUNDAY)

    restoration.emulateSavedInstanceStateRestore()

    missionShown("sun")
    missionHidden("sat")
  }

  private companion object {
    val FRIDAY: LocalDate = LocalDate.parse("2025-06-13")
    val SATURDAY: LocalDate = LocalDate.parse("2025-06-14")
    val SUNDAY: LocalDate = LocalDate.parse("2025-06-15")
    val MONDAY: LocalDate = LocalDate.parse("2025-06-16")
  }
}
