// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.mission

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionRepository
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddMissionScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val eventDay = LocalDate.of(2027, 6, 20)
  private val period =
      EventPeriod(
          title = "Local Food Drive",
          start = LocalDateTime.of(eventDay, LocalTime.of(10, 0)),
          end = LocalDateTime.of(eventDay, LocalTime.of(18, 0)),
      )
  private val teams = listOf(TeamOption("logistics", "Logistics"), TeamOption("bar", "Bar"))
  private val loaded =
      AddMissionUiState(event = period, isLoadingEvent = false, teams = teams, day = eventDay)

  private fun setContent(
      state: AddMissionUiState,
      actions: AddMissionActions = AddMissionActions(),
  ) {
    composeTestRule.setContent { ShifterTheme { AddMissionContent(state, actions) } }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  @Test
  fun showsTheEventAndItsPeriod() {
    setContent(loaded)

    node(AddMissionScreenTestTags.EVENT_TITLE).assertTextEquals("Local Food Drive")
    node(AddMissionScreenTestTags.EVENT_PERIOD).assertTextContains("10:00–18:00", substring = true)
    node(AddMissionScreenTestTags.DAY_FIELD).assertTextContains("20 Jun 2027", substring = true)
    node(AddMissionScreenTestTags.SUBMIT_BUTTON).assertIsEnabled()
  }

  @Test
  fun whileTheEventLoads_showsAProgressAndCannotSubmit() {
    setContent(AddMissionUiState())

    node(AddMissionScreenTestTags.LOADING_INDICATOR).assertIsDisplayed()
    node(AddMissionScreenTestTags.SUBMIT_BUTTON).performScrollTo().assertIsNotEnabled()
  }

  @Test
  fun whenTheEventFailsToLoad_retryIsReported() {
    var retried = false
    setContent(
        AddMissionUiState(isLoadingEvent = false, loadFailed = true),
        AddMissionActions(onRetryLoad = { retried = true }),
    )

    node(AddMissionScreenTestTags.LOAD_ERROR).assertIsDisplayed()
    node(AddMissionScreenTestTags.RETRY_BUTTON).performClick()

    assertEquals(true, retried)
  }

  @Test
  fun typingInTheFields_isReported() {
    // The fields show what the state holds, so the state follows the edits as the ViewModel would.
    var title by mutableStateOf("")
    var description by mutableStateOf("")
    composeTestRule.setContent {
      ShifterTheme {
        AddMissionContent(
            loaded.copy(title = title, description = description),
            AddMissionActions(
                onTitleChange = { title = it },
                onDescriptionChange = { description = it },
            ),
        )
      }
    }

    node(AddMissionScreenTestTags.TITLE_FIELD).performTextInput("Sort")
    node(AddMissionScreenTestTags.DESCRIPTION_FIELD).performTextInput("Check dates")

    assertEquals("Sort", title)
    assertEquals("Check dates", description)
  }

  @Test
  fun teamChips_showGeneralAndTheTeams_andReportTheChoice() {
    val choices = mutableListOf<String?>()
    setContent(
        loaded.copy(teamId = "logistics"),
        AddMissionActions(onTeamChange = { choices += it }),
    )

    node(AddMissionScreenTestTags.teamChip("logistics")).assertIsSelected()
    node(AddMissionScreenTestTags.teamChip("bar")).performClick()
    node(AddMissionScreenTestTags.GENERAL_CHIP).performClick()

    assertEquals(listOf("bar", null), choices)
  }

  @Test
  fun stepper_showsTheCount_andCannotGoBelowOne() {
    var increased = 0
    setContent(loaded, AddMissionActions(onIncreaseVolunteers = { increased++ }))

    node(AddMissionScreenTestTags.VOLUNTEERS_COUNT).performScrollTo().assertTextEquals("1")
    node(AddMissionScreenTestTags.DECREASE_VOLUNTEERS).assertIsNotEnabled()
    node(AddMissionScreenTestTags.INCREASE_VOLUNTEERS).performClick()

    assertEquals(1, increased)
  }

  @Test
  fun stepper_canDecreaseAboveOne() {
    var decreased = 0
    setContent(
        loaded.copy(volunteersNeeded = 3),
        AddMissionActions(onDecreaseVolunteers = { decreased++ }),
    )

    node(AddMissionScreenTestTags.VOLUNTEERS_COUNT).performScrollTo().assertTextEquals("3")
    node(AddMissionScreenTestTags.DECREASE_VOLUNTEERS).performClick()

    assertEquals(1, decreased)
  }

  @Test
  fun pickingTimes_reportsThem() {
    val starts = mutableListOf<LocalTime>()
    val ends = mutableListOf<LocalTime>()
    setContent(
        loaded.copy(startTime = LocalTime.of(14, 0)),
        AddMissionActions(onStartTimeChange = { starts += it }, onEndTimeChange = { ends += it }),
    )

    node(AddMissionScreenTestTags.START_FIELD).performScrollTo().assertTextEquals("14:00")
    node(AddMissionScreenTestTags.START_FIELD).performClick()
    node(AddMissionScreenTestTags.PICKER_CONFIRM).performClick()
    // The end picker opens on the start time.
    node(AddMissionScreenTestTags.END_FIELD).performClick()
    node(AddMissionScreenTestTags.PICKER_CONFIRM).performClick()

    assertEquals(listOf(LocalTime.of(14, 0)), starts)
    assertEquals(listOf(LocalTime.of(14, 0)), ends)
  }

  @Test
  fun pickingADay_reportsIt() {
    val days = mutableListOf<LocalDate>()
    setContent(loaded, AddMissionActions(onDayChange = { days += it }))

    node(AddMissionScreenTestTags.DAY_FIELD).performScrollTo().performClick()
    node(AddMissionScreenTestTags.PICKER_CONFIRM).performClick()

    assertEquals(listOf(eventDay), days)
  }

  @Test
  fun errors_areShownUnderTheirFields() {
    setContent(
        loaded.copy(
            errors =
                setOf(
                    MissionFormError.TITLE_EMPTY,
                    MissionFormError.DESCRIPTION_EMPTY,
                    MissionFormError.OUTSIDE_EVENT,
                )
        )
    )

    node(AddMissionScreenTestTags.error(MissionFormField.TITLE)).assertIsDisplayed()
    node(AddMissionScreenTestTags.error(MissionFormField.DESCRIPTION)).assertIsDisplayed()
    node(AddMissionScreenTestTags.error(MissionFormField.SCHEDULE))
        .performScrollTo()
        .assertTextEquals("The mission must take place during the event")
  }

  @Test
  fun tooLongTexts_areReportedWithTheLimit() {
    setContent(
        loaded.copy(
            errors = setOf(MissionFormError.TITLE_TOO_LONG, MissionFormError.DESCRIPTION_TOO_LONG)
        )
    )

    node(AddMissionScreenTestTags.error(MissionFormField.TITLE))
        .assertTextEquals("Use at most 80 characters")
    node(AddMissionScreenTestTags.error(MissionFormField.DESCRIPTION))
        .assertTextEquals("Use at most 2000 characters")
  }

  @Test
  fun anEndBeforeTheStart_isShownAsTheNextDay() {
    setContent(loaded.copy(startTime = LocalTime.of(22, 0), endTime = LocalTime.of(2, 0)))

    composeTestRule.onNodeWithText("Ends the next day.").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun whileSaving_showsAProgressAndDisablesTheButtons() {
    setContent(loaded.copy(isSaving = true))

    node(AddMissionScreenTestTags.SAVING_INDICATOR).assertExists()
    node(AddMissionScreenTestTags.SUBMIT_BUTTON).performScrollTo().assertIsNotEnabled()
    node(AddMissionScreenTestTags.CANCEL_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun aFailedSave_isShown() {
    setContent(loaded.copy(saveFailed = true))

    node(AddMissionScreenTestTags.SAVE_ERROR).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun submitBackAndCancel_areReported() {
    var submitted = 0
    var left = 0
    setContent(loaded, AddMissionActions(onSubmit = { submitted++ }, onBack = { left++ }))

    node(AddMissionScreenTestTags.BACK_BUTTON).performClick()
    node(AddMissionScreenTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
    node(AddMissionScreenTestTags.CANCEL_BUTTON).performScrollTo().performClick()

    assertEquals(1, submitted)
    assertEquals(2, left)
  }

  @Test
  fun fillingTheFormWithTheViewModel_addsTheMissionAndReportsIt() {
    val zone = ZoneId.of("Europe/Zurich")
    val now = Instant.parse("2027-06-01T10:00:00Z")
    val event =
        Event(
            id = "event-1",
            organizerId = "organizer-1",
            title = "Local Food Drive",
            description = "Collect food",
            type = EventType.FOOD,
            startAt = eventDay.atTime(10, 0).atZone(zone).toInstant(),
            endAt = eventDay.atTime(18, 0).atZone(zone).toInstant(),
            location = EventLocation(address = "Grand Place, Lille"),
            createdAt = now,
        )
    val missions = RecordingMissionRepository()
    val viewModel =
        AddMissionViewModel(
            eventId = "event-1",
            missionRepository = missions,
            eventRepository = SingleEventRepository(event),
            teams = teams,
            clock = Clock.fixed(now, zone),
        )
    var added by mutableStateOf<Mission?>(null)
    composeTestRule.setContent {
      ShifterTheme { AddMissionScreen(viewModel, onMissionAdded = { added = it }, onBack = {}) }
    }

    node(AddMissionScreenTestTags.TITLE_FIELD).performTextInput("Sort the donations")
    node(AddMissionScreenTestTags.DESCRIPTION_FIELD).performTextInput("Check the dates")
    node(AddMissionScreenTestTags.teamChip("logistics")).performScrollTo().performClick()
    // The start picker opens on the event start, 10:00, and the end one on the chosen start.
    node(AddMissionScreenTestTags.START_FIELD).performScrollTo().performClick()
    node(AddMissionScreenTestTags.PICKER_CONFIRM).performClick()
    node(AddMissionScreenTestTags.END_FIELD).performClick()
    node(AddMissionScreenTestTags.PICKER_CONFIRM).performClick()
    node(AddMissionScreenTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

    // 10:00–10:00 does not end after it starts, so nothing is saved yet.
    node(AddMissionScreenTestTags.error(MissionFormField.SCHEDULE)).performScrollTo()
    composeTestRule.onNodeWithText("The mission must not end when it starts").assertIsDisplayed()
    assertNull(added)

    viewModel.onEndTimeChange(LocalTime.of(11, 30))
    node(AddMissionScreenTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) { added != null }

    val mission = added!!
    assertEquals("Sort the donations", mission.title)
    assertEquals("logistics", mission.teamId)
    assertEquals(eventDay.atTime(11, 30).atZone(zone).toInstant(), mission.endAt)
    assertEquals(listOf(mission), missions.created)
  }

  @Test
  fun addedMission_isNotReportedAgainAfterRecreation() {
    val zone = ZoneId.of("Europe/Zurich")
    val now = Instant.parse("2027-06-01T10:00:00Z")
    val event =
        Event(
            id = "event-1",
            organizerId = "organizer-1",
            title = "Local Food Drive",
            description = "Collect food",
            type = EventType.FOOD,
            startAt = eventDay.atTime(10, 0).atZone(zone).toInstant(),
            endAt = eventDay.atTime(18, 0).atZone(zone).toInstant(),
            location = EventLocation(address = "Grand Place, Lille"),
            createdAt = now,
        )
    val viewModel =
        AddMissionViewModel(
            eventId = "event-1",
            missionRepository = RecordingMissionRepository(),
            eventRepository = SingleEventRepository(event),
            clock = Clock.fixed(now, zone),
        )
    var reports = 0
    val restorationTester = StateRestorationTester(composeTestRule)
    restorationTester.setContent {
      ShifterTheme { AddMissionScreen(viewModel, onMissionAdded = { reports++ }, onBack = {}) }
    }

    viewModel.onTitleChange("Sort the donations")
    viewModel.onDescriptionChange("Check the dates")
    viewModel.onStartTimeChange(LocalTime.of(11, 0))
    viewModel.onEndTimeChange(LocalTime.of(12, 0))
    node(AddMissionScreenTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) { reports > 0 }
    // Leaves and re-enters the composition with the same ViewModel, like a rotation does.
    restorationTester.emulateSavedInstanceStateRestore()
    composeTestRule.waitForIdle()

    assertEquals(1, reports)
  }
}

private class RecordingMissionRepository : MissionRepository {
  val created = mutableListOf<Mission>()

  override suspend fun createMission(mission: Mission): Mission =
      mission.copy(id = "mission-${created.size + 1}").also { created += it }

  override suspend fun getMission(eventId: String, missionId: String): Mission? =
      created.firstOrNull {
        it.id == missionId
      }

  override suspend fun getMissionsByEvent(eventId: String): List<Mission> = created.toList()
}

private class SingleEventRepository(private val event: Event) : EventRepository {
  override suspend fun createEvent(event: Event): Event = throw UnsupportedOperationException()

  override suspend fun getEvent(eventId: String): Event? = event.takeIf { it.id == eventId }

  override suspend fun getEventByJoinCode(joinCode: String): Event? = null

  override suspend fun getEventsByOrganizer(organizerId: String): List<Event> = emptyList()

  override suspend fun getEventsByMember(userId: String): List<Event> = emptyList()
}
