// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.overview

import android.Manifest
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.checkin.CheckIn
import com.swent.shifter.model.checkin.CheckInEligibility
import com.swent.shifter.model.checkin.CheckInMethod
import com.swent.shifter.model.checkin.FakeCheckInRepository
import com.swent.shifter.model.location.FakeLocationProvider
import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.model.location.LocationStatus
import com.swent.shifter.model.mission.FakeMissionRepository
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionLocation
import com.swent.shifter.model.mission.MissionRepositoryException
import com.swent.shifter.model.time.FakeMinuteClock
import com.swent.shifter.ui.checkin.AutoCheckInUiState
import com.swent.shifter.ui.checkin.AutoCheckInViewModel
import com.swent.shifter.ui.components.nowcard.NowCardTestTags
import com.swent.shifter.ui.components.nowcard.NowState
import com.swent.shifter.ui.planning.PlanningError
import com.swent.shifter.ui.planning.PlanningMission
import com.swent.shifter.ui.planning.PlanningScreenTestTags
import com.swent.shifter.ui.planning.PlanningTestTags
import com.swent.shifter.ui.planning.PlanningUiState
import com.swent.shifter.ui.planning.PlanningViewModel
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The volunteer's Overview: the now card on top of the Planning, fed by the real ViewModels. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class VolunteerOverviewTest {

  @get:Rule val compose = createComposeRule()

  /** Noon of a fixed day in the device's zone, so every mission below is today's. */
  private val noon: Instant =
      LocalDate.of(2027, 6, 20).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()
  private val clock = FakeMinuteClock(noon)
  private val missions = FakeMissionRepository()
  private val checkIns = FakeCheckInRepository()

  /** The missions opened on the map, by id. */
  private val openedOnMap = mutableListOf<String>()

  /** The missions whose manager was contacted, by id. */
  private val contacted = mutableListOf<String>()

  @After fun tearDown() = Dispatchers.resetMain()

  /**
   * Ann's Overview, with the location as [status] and the automatic check-in finding her outside
   * the zone of every mission.
   */
  private fun show(
      status: LocationStatus = LocationStatus.GRANTED,
      location: FakeLocationProvider = FakeLocationProvider(status),
  ) {
    // Unconfined, so the loads the ViewModels start have answered by the time the screen is read.
    Dispatchers.setMain(UnconfinedTestDispatcher())
    val planning = PlanningViewModel(EVENT, missions, checkIns, clock) { ANN }
    val autoCheckIn =
        AutoCheckInViewModel(
            missions,
            checkIns,
            location,
            { ANN },
            { _, _, _ -> CheckInEligibility.Ineligible(CheckInEligibility.Reason.TOO_FAR) },
            { clock.now() },
        )
    compose.setContent {
      ShifterTheme {
        VolunteerOverview(
            planning = planning,
            autoCheckIn = autoCheckIn,
            clock = clock,
            onOpenOnMap = { openedOnMap += it.id },
            onContact = { contacted += it.id },
        )
      }
    }
  }

  /** A mission of Ann's starting [startsIn] after noon, negative to have it running or over. */
  private fun annMission(
      startsIn: Duration,
      at: MissionLocation? = DESK,
      description: String = "",
      title: String = "Welcome desk",
  ) = runBlocking {
    val startAt = noon.plus(startsIn)
    missions.createMission(
        Mission(
            eventId = EVENT,
            title = title,
            description = description,
            teamId = "logistics",
            volunteersNeeded = 1,
            startAt = startAt,
            endAt = startAt.plus(Duration.ofHours(1)),
            location = at,
            assigneeIds = listOf(ANN),
            createdAt = noon,
        )
    )
  }

  private fun description(missionId: String) =
      compose.onNodeWithTag(PlanningTestTags.description(missionId), useUnmergedTree = true)

  @Test
  fun overview_showsTheNowCardAboveThePlanning() {
    annMission(Duration.ofMinutes(20))
    show()

    compose.onNodeWithTag(NowCardTestTags.CARD).assertIsDisplayed()
    compose.onNodeWithText("Starting soon").assertIsDisplayed()
    compose.onNodeWithTag(PlanningTestTags.PLANNING).assertIsDisplayed()
    val card = compose.onNodeWithTag(NowCardTestTags.CARD).getUnclippedBoundsInRoot()
    val planning = compose.onNodeWithTag(PlanningTestTags.PLANNING).getUnclippedBoundsInRoot()
    assertTrue("the card is above the Planning", card.bottom <= planning.top)
    // The automatic check-in card is gone: the now card explains it instead.
    compose.onNodeWithText("Automatic GPS check-in").assertDoesNotExist()
  }

  @Test
  fun viewOnMap_opensTheMapOnTheMission() {
    val mission = annMission(Duration.ofMinutes(20))
    show()

    compose.onNodeWithText("View on map").performClick()

    assertEquals(listOf(mission.id), openedOnMap)
  }

  @Test
  fun viewDetails_ofAMissionWithoutAPlace_expandsItsRowInThePlanning() {
    val mission = annMission(Duration.ofMinutes(20), at = null, description = "Greet the runners")
    show()
    description(mission.id).assertDoesNotExist()

    compose.onNodeWithText("View details").performClick()

    description(mission.id).assertTextEquals("Greet the runners")
    assertTrue(openedOnMap.isEmpty())
  }

  @Test
  fun viewDetails_onARowAlreadyExpanded_bringsItIntoViewAgain() {
    // Missions over earlier today, listed first, push the next one's row far below the card.
    repeat(10) { annMission(Duration.ofHours(-(it + 2L)), title = "Earlier $it") }
    val mission = annMission(Duration.ofMinutes(20), at = null, description = "Greet the runners")
    show()
    compose.onNodeWithText("View details").performClick()
    description(mission.id).assertIsDisplayed()

    compose.onNodeWithTag(NowCardTestTags.CARD).performScrollTo()
    description(mission.id).assertIsNotDisplayed()
    compose.onNodeWithText("View details").performClick()

    description(mission.id).assertIsDisplayed()
  }

  @Test
  fun tap_onAPlanningRow_showsItsDescription() {
    val mission = annMission(Duration.ofMinutes(20), description = "Greet the runners")
    show()

    compose.onNodeWithTag(PlanningTestTags.mission(mission.id)).performClick()

    description(mission.id).assertTextEquals("Greet the runners")
  }

  @Test
  fun contactYourManager_contactsTheManagerOfTheMission() {
    val mission = annMission(Duration.ofMinutes(-10))
    checkIns.add(CheckIn(EVENT, mission.id, ANN, noon, CheckInMethod.SELF, ANN))
    show()

    compose.onNodeWithText("Contact your manager").performClick()

    assertEquals(listOf(mission.id), contacted)
  }

  @Test
  fun tellYourManager_whenLate_contactsTheManagerOfTheMission() {
    val mission = annMission(Duration.ofMinutes(-10))
    show()

    compose.onNodeWithText("Tell your manager").performClick()

    assertEquals(listOf(mission.id), contacted)
  }

  @Test
  fun late_outsideTheZone_showsWhyOnTheCard() {
    annMission(Duration.ofMinutes(-10))
    val location = FakeLocationProvider()
    show(location = location)

    runBlocking { location.emit(GeoPosition(46.0, 6.0)) }
    compose.waitForIdle()

    compose.onNodeWithText("Not checked in").assertIsDisplayed()
    compose
        .onNodeWithTag(NowCardTestTags.REASON, useUnmergedTree = true)
        .assertTextEquals("You're outside the mission's zone.")
  }

  @Test
  fun checkInByAManager_isShownOnTheCard() {
    val mission = annMission(Duration.ofMinutes(-10))
    val at = noon.minus(Duration.ofMinutes(8))
    checkIns.add(CheckIn(EVENT, mission.id, ANN, at, CheckInMethod.MANAGER, "manager"))
    show()

    val time = at.atZone(ZoneId.systemDefault())
    val line = "Checked in at %02d:%02d".format(time.hour, time.minute)
    // On the card, beside the same line in the Planning.
    compose
        .onNode(
            hasText(line) and hasAnyAncestor(hasTestTag(NowCardTestTags.CARD)),
            useUnmergedTree = true,
        )
        .assertIsDisplayed()
  }

  @Test
  fun withoutTheLocationPermission_theCardAsksForIt() {
    annMission(Duration.ofMinutes(20))
    show(LocationStatus.DENIED)

    compose.onNodeWithTag(NowCardTestTags.CARD).assertIsDisplayed()
    compose.onNodeWithTag(VolunteerOverviewTestTags.LOCATION_GATE).assertIsDisplayed()
  }

  @Test
  fun withTheLocationGranted_noGateIsShown() {
    annMission(Duration.ofMinutes(20))
    show()

    compose.onNodeWithTag(VolunteerOverviewTestTags.LOCATION_GATE).assertDoesNotExist()
  }

  @Test
  fun missionsUnavailable_showsTheNotice_andRetryFollowsThemAgain() {
    annMission(Duration.ofMinutes(20))
    show()
    compose.onNodeWithTag(VolunteerOverviewTestTags.CHECK_IN_STOPPED).assertDoesNotExist()

    // The listener closes while the screen is open, as Firestore does mid-session.
    missions.failure = MissionRepositoryException.Unavailable()
    compose.waitForIdle()
    compose.onNodeWithTag(VolunteerOverviewTestTags.CHECK_IN_STOPPED).assertIsDisplayed()

    missions.failure = null
    compose.onNodeWithTag(VolunteerOverviewTestTags.CHECK_IN_RETRY).performClick()
    compose.waitForIdle()

    compose.onNodeWithTag(VolunteerOverviewTestTags.CHECK_IN_STOPPED).assertDoesNotExist()
  }

  // ── The stateless content, while the now card is not known ──

  /** A mission starting in 20 minutes, for a card in [NowState.Soon]. */
  private val soonMission =
      Mission(
          id = "soon",
          eventId = EVENT,
          title = "Welcome desk",
          description = "",
          volunteersNeeded = 1,
          startAt = noon.plus(Duration.ofMinutes(20)),
          endAt = noon.plus(Duration.ofMinutes(80)),
          location = DESK,
          createdAt = noon,
      )

  private val loadedWithACard =
      PlanningUiState(
          missions =
              listOf(
                  PlanningMission(
                      soonMission.id,
                      soonMission.title,
                      soonMission.startAt,
                      soonMission.endAt,
                  )
              ),
          isLoading = false,
          now = NowState.Soon(soonMission),
      )

  /** Every permission request is answered at once as refused, as the system dialog would. */
  private val refusingRegistry =
      object : ActivityResultRegistryOwner {
        override val activityResultRegistry =
            object : ActivityResultRegistry() {
              override fun <I, O> onLaunch(
                  requestCode: Int,
                  contract: ActivityResultContract<I, O>,
                  input: I,
                  options: ActivityOptionsCompat?,
              ) {
                dispatchResult(
                    requestCode,
                    mapOf(
                        Manifest.permission.ACCESS_FINE_LOCATION to false,
                        Manifest.permission.ACCESS_COARSE_LOCATION to false,
                    ),
                )
              }
            }
      }

  private fun showContent(
      planning: () -> PlanningUiState,
      location: LocationStatus = LocationStatus.DENIED,
      onRetry: () -> Unit = {},
  ) {
    compose.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides refusingRegistry) {
        ShifterTheme {
          VolunteerOverviewContent(
              planning = planning(),
              checkIn = AutoCheckInUiState(locationStatus = location),
              now = noon,
              expandedMissionId = null,
              onMissionClick = {},
              onOpenMission = {},
              onContact = {},
              onRefreshLocation = {},
              onRetry = onRetry,
          )
        }
      }
    }
  }

  @Test
  fun loading_showsNoCard_butTheGateAndThePlanningLoading() {
    showContent({ PlanningUiState(isLoading = true) })

    compose.onNodeWithTag(NowCardTestTags.CARD).assertDoesNotExist()
    compose.onNodeWithTag(VolunteerOverviewTestTags.LOCATION_GATE).assertIsDisplayed()
    compose.onNodeWithTag(PlanningScreenTestTags.LOADING).assertIsDisplayed()
  }

  @Test
  fun planningError_showsNoCard_andItsRetryLoadsAgain() {
    var retries = 0
    showContent(
        { PlanningUiState(isLoading = false, error = PlanningError.OFFLINE) },
        onRetry = { retries++ },
    )

    compose.onNodeWithTag(NowCardTestTags.CARD).assertDoesNotExist()
    compose.onNodeWithTag(VolunteerOverviewTestTags.LOCATION_GATE).assertIsDisplayed()
    compose.onNodeWithTag(PlanningScreenTestTags.RETRY_BUTTON).performClick()

    assertEquals(1, retries)
  }

  @Test
  fun gate_whenTheCardAppears_keepsThePermissionAlreadyAsked() {
    var planning by mutableStateOf(PlanningUiState(isLoading = true))
    showContent({ planning })
    compose.onNodeWithText("Allow location").performClick()
    compose.onNodeWithText("Open settings").assertIsDisplayed()

    planning = loadedWithACard
    compose.waitForIdle()

    compose.onNodeWithTag(NowCardTestTags.CARD).assertIsDisplayed()
    compose.onNodeWithText("Open settings").assertIsDisplayed()
    compose.onNodeWithText("Allow location").assertDoesNotExist()
  }

  private companion object {
    const val EVENT = "event-1"
    const val ANN = "ann"
    val DESK = MissionLocation("Welcome desk", GeoPosition(46.52, 6.57))
  }
}
