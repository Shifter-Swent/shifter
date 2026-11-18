// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.shifter.R
import com.swent.shifter.model.location.LocationStatus
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.time.MinuteClock
import com.swent.shifter.ui.checkin.AutoCheckInUiState
import com.swent.shifter.ui.checkin.AutoCheckInViewModel
import com.swent.shifter.ui.checkin.whyNotCheckedIn
import com.swent.shifter.ui.components.nowcard.NowCard
import com.swent.shifter.ui.components.nowcard.NowState
import com.swent.shifter.ui.components.time.rememberNow
import com.swent.shifter.ui.location.LocationPermissionGate
import com.swent.shifter.ui.planning.PlanningContent
import com.swent.shifter.ui.planning.PlanningUiState
import com.swent.shifter.ui.planning.PlanningViewModel
import com.swent.shifter.ui.theme.shifter_mutedText
import java.time.Instant

object VolunteerOverviewTestTags {
  const val SCREEN = "volunteerOverview"
  /** The location permission gate, under the now card or alone while its state is unknown. */
  const val LOCATION_GATE = "volunteerOverviewLocationGate"
  /** The notice shown while the automatic check-in cannot follow the missions. */
  const val CHECK_IN_STOPPED = "volunteerOverviewCheckInStopped"
  const val CHECK_IN_RETRY = "volunteerOverviewCheckInRetry"
}

/**
 * The Overview of an event for a volunteer: the now card on top, then their Planning, as one page
 * that scrolls.
 *
 * One [planning] ViewModel feeds both, so the missions and check-ins are read once. The automatic
 * check-in runs while this screen is shown: [autoCheckIn] only reads positions while its state is
 * collected. Both states are collected with the lifecycle, so the clock and the GPS stop when the
 * app goes to the background.
 *
 * @param clock moves the card's countdowns on each minute.
 * @param onOpenOnMap opens the Map tab on a mission that has a place.
 * @param onContact the card's "Contact your manager" or "Tell your manager", for its mission.
 */
@Composable
fun VolunteerOverview(
    planning: PlanningViewModel,
    autoCheckIn: AutoCheckInViewModel,
    clock: MinuteClock,
    onOpenOnMap: (Mission) -> Unit,
    onContact: (Mission) -> Unit,
    modifier: Modifier = Modifier,
) {
  val planningState by planning.uiState.collectAsStateWithLifecycle()
  val checkInState by autoCheckIn.uiState.collectAsStateWithLifecycle()
  var expanded by rememberSaveable { mutableStateOf<String?>(null) }
  // Counts the card's "View details", so the row scrolls into view even when already expanded.
  var revealRequest by rememberSaveable { mutableIntStateOf(0) }
  VolunteerOverviewContent(
      planning = planningState,
      checkIn = checkInState,
      now = rememberNow(clock),
      expandedMissionId = expanded,
      revealRequest = revealRequest,
      onMissionClick = { id -> expanded = if (expanded == id) null else id },
      onOpenMission = { mission ->
        if (mission.location != null) onOpenOnMap(mission)
        else {
          expanded = mission.id
          revealRequest++
        }
      },
      onContact = onContact,
      onRefreshLocation = autoCheckIn::refreshLocation,
      onRetryCheckIn = autoCheckIn::retry,
      onRetry = planning::load,
      modifier = modifier,
  )
}

/**
 * The stateless Overview of a volunteer: the now card once [PlanningUiState.now] is known, then the
 * location permission gate while the location cannot be read, a notice while the automatic check-in
 * has stopped, and the Planning.
 *
 * @param now the clock's tick, for the card's countdowns.
 * @param expandedMissionId the mission whose row in the Planning shows its details.
 * @param revealRequest a new value brings the expanded row into view again.
 * @param onMissionClick called with the id of a row of the Planning tapped.
 * @param onOpenMission the card's "View on map" or "View details".
 * @param onContact the card's "Contact your manager" or "Tell your manager".
 * @param onRefreshLocation reads the permission and the location services again.
 * @param onRetryCheckIn follows the missions again for the automatic check-in.
 * @param onRetry loads the Planning again after an error.
 */
@Composable
fun VolunteerOverviewContent(
    planning: PlanningUiState,
    checkIn: AutoCheckInUiState,
    now: Instant,
    expandedMissionId: String?,
    onMissionClick: (String) -> Unit,
    onOpenMission: (Mission) -> Unit,
    onContact: (Mission) -> Unit,
    onRefreshLocation: () -> Unit,
    onRetry: () -> Unit,
    onRetryCheckIn: () -> Unit = {},
    revealRequest: Int = 0,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(16.dp)
              .testTag(VolunteerOverviewTestTags.SCREEN)
  ) {
    val state = planning.now
    // Not known yet, or the missions failed to load: the Planning below says which.
    if (state != null) {
      NowCard(
          state = state,
          now = now,
          checkedInAt = planning.checkedInAt(state),
          notCheckedIn = (state as? NowState.Late)?.let { checkIn.whyNotCheckedIn(it.current) },
          onOpenMission = onOpenMission,
          onContact = onContact,
      )
    }
    // Called from here only, whether the card is shown or not, so the gate keeps its state (a
    // permission already asked) when the card comes or goes.
    if (checkIn.locationStatus != LocationStatus.GRANTED) {
      LocationPermissionGate(
          status = checkIn.locationStatus,
          onRefreshStatus = onRefreshLocation,
          modifier = Modifier.padding(top = 12.dp).testTag(VolunteerOverviewTestTags.LOCATION_GATE),
      )
    }
    if (checkIn.missionsUnavailable) CheckInStopped(onRetryCheckIn)
    PlanningContent(
        state = planning,
        onRetry = onRetry,
        expandedMissionId = expandedMissionId,
        onMissionClick = onMissionClick,
        revealRequest = revealRequest,
    )
  }
}

/** The automatic check-in no longer follows the missions: no check-in is tried until [onRetry]. */
@Composable
private fun CheckInStopped(onRetry: () -> Unit) {
  Row(
      modifier = Modifier.padding(top = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
        text = stringResource(R.string.auto_check_in_stopped),
        style = MaterialTheme.typography.bodySmall,
        color = shifter_mutedText,
        modifier = Modifier.weight(1f).testTag(VolunteerOverviewTestTags.CHECK_IN_STOPPED),
    )
    TextButton(
        onClick = onRetry,
        modifier = Modifier.testTag(VolunteerOverviewTestTags.CHECK_IN_RETRY),
    ) {
      Text(stringResource(R.string.auto_check_in_retry), fontWeight = FontWeight.SemiBold)
    }
  }
}

/**
 * When the volunteer was checked in to the mission [state] is about, from the check-in records, so
 * a check-in by GPS or by a manager both count: the running mission, or the next one after an early
 * check-in.
 */
private fun PlanningUiState.checkedInAt(state: NowState): Instant? {
  val missionId =
      when (state) {
        is NowState.Ongoing -> state.current.id
        is NowState.Soon -> state.next.id
        else -> return null
      }
  return missions.firstOrNull { it.id == missionId }?.checkedInAt
}
