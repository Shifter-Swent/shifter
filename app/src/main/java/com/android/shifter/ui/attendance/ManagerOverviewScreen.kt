// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.attendance

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.model.checkin.checkInWindow
import com.swent.shifter.ui.components.FormPrimaryButton
import com.swent.shifter.ui.theme.shifter_mutedText
import com.swent.shifter.ui.theme.shifter_success
import com.swent.shifter.ui.theme.shifter_warning
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ManagerOverviewTestTags {
  const val SCREEN = "managerOverviewScreen"
  const val TITLE = "managerOverviewTitle"
  const val SUBTITLE = "managerOverviewSubtitle"
  const val ALL_TEAMS_CHIP = "managerOverviewTeamChip_All"
  const val MISSION_COUNT = "managerOverviewMissionCount"
  const val LOADING = "managerOverviewLoading"
  const val LOAD_ERROR = "managerOverviewLoadError"
  const val RETRY = "managerOverviewRetry"
  const val EMPTY = "managerOverviewEmpty"
  const val MARK_COMPLETED_DIALOG = "managerOverviewMarkCompletedDialog"
  const val MARK_COMPLETED_CONFIRM = "managerOverviewMarkCompletedConfirm"
  const val MARK_COMPLETED_CANCEL = "managerOverviewMarkCompletedCancel"
  const val ADD_MISSION_BUTTON = "managerOverviewAddMissionButton"

  fun teamChip(teamId: String) = "managerOverviewTeamChip_$teamId"

  fun mission(missionId: String) = "managerOverviewMission_$missionId"

  /** The tappable part of a mission card: what expands and collapses it. */
  fun missionHeader(missionId: String) = "managerOverviewMissionHeader_$missionId"

  fun counter(missionId: String) = "managerOverviewCounter_$missionId"

  fun status(missionId: String) = "managerOverviewStatus_$missionId"

  fun present(missionId: String) = "managerOverviewPresent_$missionId"

  /** Those not there yet, before the mission starts: expected rather than [missing]. */
  fun expected(missionId: String) = "managerOverviewExpected_$missionId"

  fun missing(missionId: String) = "managerOverviewMissing_$missionId"

  /** The name of a missing assignee, which checks them in when tapped. */
  fun checkIn(missionId: String, userId: String) = "managerOverviewCheckIn_${missionId}_$userId"

  /** Shown on a present assignee whose check-in is not confirmed by the backend yet. */
  fun pending(missionId: String, userId: String) = "managerOverviewPending_${missionId}_$userId"

  /** Shown on a present assignee checked in by a manager or the organizer. */
  fun manual(missionId: String, userId: String) = "managerOverviewManual_${missionId}_$userId"

  const val PENDING_BANNER = "managerOverviewPendingBanner"
  const val SNACKBAR = "managerOverviewSnackbar"

  fun markCompleted(missionId: String) = "managerOverviewMarkCompleted_$missionId"
}

/** What a card says about the attendance of its mission; the first case that holds wins. */
private enum class AttendanceStatus {
  /** Closed by hand or past its end: its attendance is history, and no warning can be acted on. */
  COMPLETED,
  /** The check-in has not opened, so nobody can be present yet, nor late. */
  NOT_OPEN,
  /** The check-ins of the mission could not be observed. */
  FAILED,
  /** The check-ins of the mission have not arrived yet. */
  WAITING,
  /** The check-in is open but the mission has not started: those not there are not late. */
  EARLY,
  /** Fewer people are present than the mission needs, which the manager is warned about. */
  UNDERSTAFFED,
  /** Enough people are present: nothing to report. */
  STAFFED,
}

/** Keeps the cards opened across a configuration change, as a list a Bundle can hold. */
private val ExpandedIdsSaver: Saver<Set<String>, ArrayList<String>> =
    Saver(save = { ArrayList(it) }, restore = { it.toSet() })

/**
 * The manager's overview of an event: the missions of the teams they manage, by start time, each
 * with who is present. Stateless but for which team is filtered on and which cards are expanded,
 * both kept across a configuration change.
 *
 * @param now when the screen is read, which decides whose check-in is open and who is late. Nothing
 *   ticks here: the caller must pass a [Instant] it refreshes regularly, every minute or so, or a
 *   mission will stay "Check-in not open yet" long after it has opened.
 * @param zone the time zone the times are shown in, the device's one in the app.
 * @param onCheckIn checks a missing assignee in by hand, when their name is tapped.
 * @param onUndoCheckIn takes back the manual check-in of [AttendanceUiState.undoableCheckIn].
 * @param onUndoWindowOver called once its "Undo" is gone unused, which sends that check-in.
 * @param onCheckInErrorShown called once the oldest of [AttendanceUiState.checkInErrors] has been
 *   shown.
 * @param onMarkCompleted called with a mission the manager has confirmed closing.
 * @param onCompletionFailureShown called once the failure to close a mission has been reported.
 * @param onAddMission opens the mission creation form. Its button is shown only when it is given,
 *   and only to a manager: the organizer adds missions from their own Overview.
 */
@Composable
fun ManagerOverviewContent(
    state: AttendanceUiState,
    eventTitle: String,
    now: Instant,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    onCheckIn: (missionId: String, userId: String) -> Unit = { _, _ -> },
    onUndoCheckIn: () -> Unit = {},
    onUndoWindowOver: () -> Unit = {},
    onCheckInErrorShown: () -> Unit = {},
    onMarkCompleted: (String) -> Unit = {},
    onCompletionFailureShown: () -> Unit = {},
    onAddMission: (() -> Unit)? = null,
) {
  // The teams of the missions the user may watch: no query of its own, and none invented.
  val teams =
      remember(state.missions) {
        state.missions
            .mapNotNull { mission -> mission.teamId?.let { it to mission.teamName } }
            .distinctBy { it.first }
      }
  var pickedTeamId by rememberSaveable { mutableStateOf<String?>(null) }
  // By id, so filtering a team out and back leaves the cards opened as they were.
  var expandedMissionIds by
      rememberSaveable(stateSaver = ExpandedIdsSaver) { mutableStateOf(emptySet<String>()) }
  // A team whose missions are gone, e.g. after a refresh, stops filtering them all away.
  val selectedTeamId = pickedTeamId?.takeIf { id -> teams.any { it.first == id } }
  val missions = state.missions.filter { selectedTeamId == null || it.teamId == selectedTeamId }
  // By id, so the question survives a configuration change.
  var confirmingMissionId by rememberSaveable { mutableStateOf<String?>(null) }
  // One host for every message of the screen, so they are shown one after the other rather than
  // over each other.
  val snackbarHostState = remember { SnackbarHostState() }
  val completionFailed = stringResource(R.string.attendance_mark_completed_failed)
  val retry = stringResource(R.string.attendance_retry)
  LaunchedEffect(state.completionFailedMissionId) {
    val missionId = state.completionFailedMissionId ?: return@LaunchedEffect
    // With an action, the default duration would never dismiss it.
    val result =
        snackbarHostState.showSnackbar(
            completionFailed,
            actionLabel = retry,
            duration = SnackbarDuration.Long,
        )
    onCompletionFailureShown()
    if (result == SnackbarResult.ActionPerformed) onMarkCompleted(missionId)
  }
  CheckInMessages(
      host = snackbarHostState,
      undoable = state.undoableCheckIn,
      failures = state.checkInErrors,
      onUndo = onUndoCheckIn,
      onUndoWindowOver = onUndoWindowOver,
      onErrorShown = onCheckInErrorShown,
  )
  Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(ManagerOverviewTestTags.SCREEN),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item { Header(eventTitle, state.isOrganizer) }
      if (onAddMission != null && !state.isOrganizer) {
        item {
          FormPrimaryButton(
              text = stringResource(R.string.overview_add_mission),
              onClick = onAddMission,
              testTag = ManagerOverviewTestTags.ADD_MISSION_BUTTON,
          )
        }
      }
      if (state.loadFailed) item { LoadError(onRetry) }
      if (state.pendingCheckInCount > 0) item { PendingBanner(state.pendingCheckInCount) }
      item { TeamFilters(teams, selectedTeamId) { pickedTeamId = it } }
      item {
        // Never "0 missions" before there is an answer: that reads as having none.
        MissionsHeader(missions.size.takeUnless { it == 0 && !state.isLoaded })
      }
      items(missions, key = { it.id }) { mission ->
        MissionCard(
            mission = mission,
            status = mission.status(now),
            expanded = mission.id in expandedMissionIds,
            canMarkCompleted = !state.isOrganizer && mission.canBeMarkedCompleted(now),
            isMarkingCompleted = mission.id in state.markingCompletedIds,
            now = now,
            zone = zone,
            // Each card on its own: opening one leaves the others as they are.
            onToggle = {
              expandedMissionIds =
                  if (mission.id in expandedMissionIds) expandedMissionIds - mission.id
                  else expandedMissionIds + mission.id
            },
            onMarkCompleted = { confirmingMissionId = mission.id },
            onCheckIn = { userId -> onCheckIn(mission.id, userId) },
        )
      }
      if (missions.isEmpty()) {
        item {
          when {
            // A reload keeps the cards already shown instead of replacing them with the spinner.
            state.isLoading ->
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp).testTag(ManagerOverviewTestTags.LOADING),
                    strokeWidth = 2.dp,
                )
            // Already "loaded, did not fail, nothing there". A team that was picked always has
            // a mission, since the chips come from the missions, so it alone never empties the
            // list.
            state.isEmpty -> EmptyMessage(R.string.attendance_no_missions)
          }
        }
      }
    }
    SnackbarHost(
        snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).testTag(ManagerOverviewTestTags.SNACKBAR),
    )
  }
  confirmingMissionId?.let { missionId ->
    MarkCompletedDialog(
        onConfirm = {
          confirmingMissionId = null
          onMarkCompleted(missionId)
        },
        onDismiss = { confirmingMissionId = null },
    )
  }
}

/**
 * Shows in [host] the "checked in · Undo" action of the last manual check-in, and why check-ins
 * failed, one after the other, oldest first.
 *
 * The snackbar decides when the undo window ends: the check-in is only sent once "Undo" is gone
 * ([onUndoWindowOver]), so it can never be tapped for a check-in already sent, however long an
 * accessibility setting keeps it on screen.
 */
@Composable
private fun CheckInMessages(
    host: SnackbarHostState,
    undoable: UndoableCheckIn?,
    failures: List<ManualCheckInFailure>,
    onUndo: () -> Unit,
    onUndoWindowOver: () -> Unit,
    onErrorShown: () -> Unit,
) {
  val unnamed = stringResource(R.string.attendance_volunteer_unknown)
  val checkedIn = undoable?.let {
    stringResource(R.string.attendance_checked_in, it.displayName?.ifBlank { null } ?: unnamed)
  }
  val undo = stringResource(R.string.attendance_undo)
  LaunchedEffect(undoable) {
    // Only an "Undo" of an earlier check-in, never a failure that is still being shown.
    host.currentSnackbarData?.takeIf { it.visuals.actionLabel == undo }?.dismiss()
    if (checkedIn != null) {
      when (host.showSnackbar(checkedIn, actionLabel = undo, duration = SnackbarDuration.Short)) {
        SnackbarResult.ActionPerformed -> onUndo()
        SnackbarResult.Dismissed -> onUndoWindowOver()
      }
    }
  }
  val failure = failures.firstOrNull()
  val errorMessage = failure?.let {
    stringResource(
        when (it.reason) {
          ManualCheckInError.NOT_ALLOWED -> R.string.attendance_check_in_not_allowed
          ManualCheckInError.FAILED -> R.string.attendance_check_in_failed
          ManualCheckInError.OUTSIDE_WINDOW -> R.string.attendance_check_in_outside_window
        },
        it.displayName?.ifBlank { null } ?: unnamed,
    )
  }
  // Keyed on the whole list, not its first failure: once that is shown and taken out, an equal one
  // behind it, such as the same volunteer refused twice, must still be shown. A failure added while
  // one is shown shows that one again from the start, so none is cut short.
  LaunchedEffect(failures) {
    if (errorMessage != null) {
      host.showSnackbar(errorMessage, duration = SnackbarDuration.Short)
      onErrorShown()
    }
  }
}

/** Asks before closing a mission, since it cannot be undone. */
@Composable
private fun MarkCompletedDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
      onDismissRequest = onDismiss,
      modifier = Modifier.testTag(ManagerOverviewTestTags.MARK_COMPLETED_DIALOG),
      title = { Text(stringResource(R.string.attendance_mark_completed_title)) },
      text = { Text(stringResource(R.string.attendance_mark_completed_message)) },
      confirmButton = {
        TextButton(
            onClick = onConfirm,
            modifier = Modifier.testTag(ManagerOverviewTestTags.MARK_COMPLETED_CONFIRM),
        ) {
          Text(stringResource(R.string.attendance_mark_completed))
        }
      },
      dismissButton = {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag(ManagerOverviewTestTags.MARK_COMPLETED_CANCEL),
        ) {
          Text(stringResource(R.string.attendance_mark_completed_cancel))
        }
      },
  )
}

/** Whether the load has come back with missions to count: neither running, nor failed. */
private val AttendanceUiState.isLoaded: Boolean
  get() = !isLoading && !loadFailed

/**
 * The status of [this] at [now]. The check-in window of the mission is read first, from the shared
 * [checkInWindow], so a mission nobody can have checked in to yet and one that is over are never
 * reported short of volunteers: the warning would be about nothing, or no longer actionable.
 */
private fun AttendanceMission.status(now: Instant): AttendanceStatus {
  val window = checkInWindow(startAt, endAt)
  return when {
    isCompleted(now) -> AttendanceStatus.COMPLETED
    now < window.start -> AttendanceStatus.NOT_OPEN
    attendanceFailed -> AttendanceStatus.FAILED
    !attendanceKnown -> AttendanceStatus.WAITING
    now < startAt -> AttendanceStatus.EARLY
    isUnderstaffed -> AttendanceStatus.UNDERSTAFFED
    else -> AttendanceStatus.STAFFED
  }
}

/**
 * The event being watched, named after the part the reader has in it; the tabs are navigation's.
 */
@Composable
private fun Header(eventTitle: String, isOrganizer: Boolean) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
        text = eventTitle,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.testTag(ManagerOverviewTestTags.TITLE),
    )
    Text(
        text =
            stringResource(
                if (isOrganizer) R.string.attendance_subtitle_organizer
                else R.string.attendance_subtitle
            ),
        style = MaterialTheme.typography.bodySmall,
        color = shifter_mutedText,
        modifier = Modifier.testTag(ManagerOverviewTestTags.SUBTITLE),
    )
  }
}

/**
 * Check-ins recorded on this phone and not sent yet. Firestore only sends them while the app runs,
 * so the manager is asked to keep it open rather than to assume they are saved.
 */
@Composable
private fun PendingBanner(count: Int) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
        Icons.Filled.CloudOff,
        contentDescription = null,
        tint = shifter_warning,
        modifier = Modifier.size(16.dp),
    )
    Text(
        text = pluralStringResource(R.plurals.attendance_pending_check_ins, count, count),
        style = MaterialTheme.typography.bodySmall,
        color = shifter_warning,
        modifier = Modifier.testTag(ManagerOverviewTestTags.PENDING_BANNER),
    )
  }
}

@Composable
private fun EmptyMessage(text: Int) {
  Text(
      text = stringResource(text),
      style = MaterialTheme.typography.bodySmall,
      color = shifter_mutedText,
      modifier = Modifier.testTag(ManagerOverviewTestTags.EMPTY),
  )
}

@Composable
private fun LoadError(onRetry: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
        text = stringResource(R.string.attendance_load_failed),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.weight(1f).testTag(ManagerOverviewTestTags.LOAD_ERROR),
    )
    TextButton(onClick = onRetry, modifier = Modifier.testTag(ManagerOverviewTestTags.RETRY)) {
      Text(stringResource(R.string.attendance_retry))
    }
  }
}

/** "All" followed by the teams of the listed missions, by id; exactly one is selected. */
@Composable
private fun TeamFilters(
    teams: List<Pair<String, String?>>,
    selectedTeamId: String?,
    onSelectTeam: (String?) -> Unit,
) {
  Row(
      // One group of choices, so a screen reader announces them as such.
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    TeamChip(
        label = stringResource(R.string.attendance_filter_all),
        selected = selectedTeamId == null,
        onClick = { onSelectTeam(null) },
        testTag = ManagerOverviewTestTags.ALL_TEAMS_CHIP,
    )
    teams.forEach { (teamId, teamName) ->
      TeamChip(
          // A team that no longer exists keeps its missions filterable, under no name of its own.
          label = teamName ?: stringResource(R.string.attendance_team_unknown),
          selected = teamId == selectedTeamId,
          onClick = { onSelectTeam(teamId) },
          testTag = ManagerOverviewTestTags.teamChip(teamId),
      )
    }
  }
}

@Composable
private fun TeamChip(label: String, selected: Boolean, onClick: () -> Unit, testTag: String) {
  val colors = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(20.dp)
  Box(
      modifier =
          Modifier.clip(shape)
              .background(if (selected) colors.primaryContainer else colors.surfaceVariant)
              .border(1.dp, if (selected) colors.primary else colors.outline, shape)
              .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
              .padding(horizontal = 12.dp, vertical = 7.dp)
              .testTag(testTag)
  ) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) colors.primary else colors.onSurfaceVariant,
    )
  }
}

/** The missions listed, counted when there is an answer to count; [count] is null until then. */
@Composable
private fun MissionsHeader(count: Int?) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
        text = stringResource(R.string.attendance_missions_title),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.weight(1f),
    )
    if (count != null) {
      Text(
          text = pluralStringResource(R.plurals.attendance_mission_count, count, count),
          style = MaterialTheme.typography.labelSmall,
          color = shifter_mutedText,
          modifier = Modifier.testTag(ManagerOverviewTestTags.MISSION_COUNT),
      )
    }
  }
}

/**
 * One mission and its live attendance. Only the header is tappable, so the counter and the lists
 * keep their own accessibility nodes instead of being merged into the card; and it is tappable only
 * when there is something under it, so the arrow never flips on an empty card.
 */
@Composable
private fun MissionCard(
    mission: AttendanceMission,
    status: AttendanceStatus,
    expanded: Boolean,
    canMarkCompleted: Boolean,
    isMarkingCompleted: Boolean,
    now: Instant,
    zone: ZoneId,
    onToggle: () -> Unit,
    onMarkCompleted: () -> Unit,
    onCheckIn: (userId: String) -> Unit,
) {
  val shape = RoundedCornerShape(18.dp)
  // Nobody can be present before the check-in opens, and nobody is known while it is not.
  val expandable = mission.attendanceKnown && status != AttendanceStatus.NOT_OPEN
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .clip(shape)
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .border(1.dp, MaterialTheme.colorScheme.outline, shape)
              .padding(16.dp)
              .testTag(ManagerOverviewTestTags.mission(mission.id)),
      verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .then(
                    if (!expandable) Modifier
                    else
                        Modifier.clickable(
                            onClickLabel =
                                stringResource(
                                    if (expanded) R.string.attendance_collapse
                                    else R.string.attendance_expand
                                ),
                            onClick = onToggle,
                        )
                )
                .testTag(ManagerOverviewTestTags.missionHeader(mission.id)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = mission.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text =
                stringResource(
                    R.string.attendance_mission_subtitle,
                    when {
                      mission.teamId == null -> stringResource(R.string.attendance_team_general)
                      else -> mission.teamName ?: stringResource(R.string.attendance_team_unknown)
                    },
                    schedule(mission, now, zone),
                ),
            style = MaterialTheme.typography.labelSmall,
            color = shifter_mutedText,
        )
      }
      if (expandable) {
        Icon(
            imageVector =
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = shifter_mutedText,
        )
      }
    }

    // The label sits directly above the count.
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
          text = stringResource(R.string.attendance_live_check_in),
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          color = shifter_mutedText,
          letterSpacing = 1.sp,
      )
      Row(verticalAlignment = Alignment.CenterVertically) {
        val unknown =
            stringResource(
                R.string.attendance_count_unknown_description,
                mission.volunteersNeeded,
            )
        Text(
            text =
                stringResource(
                    R.string.attendance_counter,
                    // An unknown attendance is not nobody present, so it is not counted as 0.
                    if (mission.attendanceKnown) mission.presentCount.toString()
                    else stringResource(R.string.attendance_count_unknown),
                    mission.volunteersNeeded,
                ),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier.weight(1f)
                    // A dash reads as nothing at all, so it is spelled out instead.
                    .then(
                        if (mission.attendanceKnown) Modifier
                        else Modifier.semantics { contentDescription = unknown }
                    )
                    .testTag(ManagerOverviewTestTags.counter(mission.id)),
        )
        StatusLabel(status, ManagerOverviewTestTags.status(mission.id))
      }
    }

    if (expanded && expandable) {
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      People(
          label = stringResource(R.string.attendance_present, mission.presentCount),
          people = mission.presentAssignees,
          icon = Icons.Filled.Check,
          tint = shifter_success,
          zone = zone,
          testTag = ManagerOverviewTestTags.present(mission.id),
          missionId = mission.id,
      )
      if (mission.missingAssignees.isNotEmpty()) {
        // Before the start they are merely awaited; only once it has begun are they late.
        val early = status == AttendanceStatus.EARLY
        People(
            missionId = mission.id,
            // Once the mission is completed, by hand or past its end, a check-in would record the
            // tap rather than an arrival, so the names are no longer tappable, and the section says
            // so.
            onCheckIn = onCheckIn.takeUnless { status == AttendanceStatus.COMPLETED },
            hint =
                if (status == AttendanceStatus.COMPLETED) R.string.attendance_check_in_closed
                else R.string.attendance_tap_to_check_in,
            label =
                stringResource(
                    if (early) R.string.attendance_expected else R.string.attendance_missing,
                    mission.missingAssignees.size,
                ),
            people = mission.missingAssignees,
            icon = Icons.Filled.RadioButtonUnchecked,
            tint = if (early) shifter_mutedText else shifter_warning,
            zone = zone,
            testTag =
                if (early) ManagerOverviewTestTags.expected(mission.id)
                else ManagerOverviewTestTags.missing(mission.id),
        )
      }
    }

    if (canMarkCompleted) {
      FormPrimaryButton(
          text = stringResource(R.string.attendance_mark_completed),
          onClick = onMarkCompleted,
          testTag = ManagerOverviewTestTags.markCompleted(mission.id),
          enabled = !isMarkingCompleted,
          isLoading = isMarkingCompleted,
      )
    }
  }
}

/** Nothing at all when there is nothing to report, so a staffed mission stays quiet. */
@Composable
private fun StatusLabel(status: AttendanceStatus, testTag: String) {
  val label: Int
  val color: Color
  var icon: ImageVector? = null
  when (status) {
    AttendanceStatus.NOT_OPEN -> {
      label = R.string.attendance_check_in_not_open
      color = shifter_mutedText
    }
    AttendanceStatus.FAILED -> {
      label = R.string.attendance_unavailable
      color = MaterialTheme.colorScheme.error
    }
    AttendanceStatus.WAITING -> {
      label = R.string.attendance_waiting
      color = shifter_mutedText
    }
    AttendanceStatus.EARLY -> {
      label = R.string.attendance_check_in_open
      color = shifter_mutedText
    }
    AttendanceStatus.COMPLETED -> {
      label = R.string.attendance_completed
      color = shifter_success
      icon = Icons.Filled.Check
    }
    AttendanceStatus.UNDERSTAFFED -> {
      label = R.string.attendance_understaffed
      color = shifter_warning
      icon = Icons.Filled.Warning
    }
    AttendanceStatus.STAFFED -> return
  }
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    icon?.let { Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(14.dp)) }
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.testTag(testTag),
    )
  }
}

/** The people of one section of an expanded card, with their arrival time when they have one. */
@Composable
private fun People(
    label: String,
    people: List<AttendanceVolunteer>,
    icon: ImageVector,
    tint: Color,
    zone: ZoneId,
    testTag: String,
    missionId: String,
    onCheckIn: ((userId: String) -> Unit)? = null,
    @StringRes hint: Int? = null,
) {
  Column(modifier = Modifier.testTag(testTag), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = shifter_mutedText,
    )
    // Once for the section rather than on every name; each name still says it to screen readers.
    if (hint != null) {
      Text(
          text = stringResource(hint),
          style = MaterialTheme.typography.labelSmall,
          color = shifter_mutedText,
      )
    }
    people.forEach { volunteer ->
      // An id is not a name, so someone whose profile could not be read stays unnamed.
      val name =
          volunteer.displayName?.takeIf { it.isNotBlank() }
              ?: stringResource(R.string.attendance_volunteer_unknown)
      Row(
          modifier =
              Modifier.fillMaxWidth()
                  .then(
                      if (onCheckIn == null) Modifier
                      // 48 dp tall at least, since one tap writes a check-in that cannot be
                      // changed: a hurried tap must not land on the next name.
                      else
                          Modifier.heightIn(min = 48.dp)
                              .clip(RoundedCornerShape(8.dp))
                              .clickable(
                                  onClickLabel =
                                      stringResource(R.string.attendance_check_in_action, name),
                                  role = Role.Button,
                              ) {
                                onCheckIn(volunteer.userId)
                              }
                              .testTag(ManagerOverviewTestTags.checkIn(missionId, volunteer.userId))
                  ),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (volunteer.checkedInManually) {
          Text(
              text = stringResource(R.string.attendance_manual),
              style = MaterialTheme.typography.labelSmall,
              color = shifter_mutedText,
              modifier =
                  Modifier.testTag(ManagerOverviewTestTags.manual(missionId, volunteer.userId)),
          )
        }
        if (volunteer.checkInPending) {
          // Not confirmed yet: in its undo window, or waiting for the network.
          Icon(
              Icons.Filled.CloudOff,
              contentDescription = stringResource(R.string.attendance_not_synced),
              tint = shifter_mutedText,
              modifier =
                  Modifier.size(14.dp)
                      .testTag(ManagerOverviewTestTags.pending(missionId, volunteer.userId)),
          )
        }
        volunteer.checkedInAt?.let {
          Text(
              text = TIME.format(it.atZone(zone)),
              style = MaterialTheme.typography.labelSmall,
              color = shifter_mutedText,
          )
        }
      }
    }
  }
}

/**
 * When the mission runs: its times alone on the day being read, the day added on any other, and
 * both days when it crosses midnight, so two missions of a long event never read the same.
 */
@Composable
private fun schedule(mission: AttendanceMission, now: Instant, zone: ZoneId): String {
  val start = mission.startAt.atZone(zone)
  val end = mission.endAt.atZone(zone)
  return when {
    start.toLocalDate() != end.toLocalDate() ->
        stringResource(
            R.string.attendance_schedule_across_days,
            DAY.format(start),
            TIME.format(start),
            DAY.format(end),
            TIME.format(end),
        )
    start.toLocalDate() != now.atZone(zone).toLocalDate() ->
        stringResource(
            R.string.attendance_schedule_on_day,
            DAY.format(start),
            TIME.format(start),
            TIME.format(end),
        )
    else -> stringResource(R.string.attendance_schedule, TIME.format(start), TIME.format(end))
  }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
