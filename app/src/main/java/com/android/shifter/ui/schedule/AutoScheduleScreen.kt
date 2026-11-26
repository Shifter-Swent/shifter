// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.model.time.MinuteClock
import com.swent.shifter.ui.components.LoadErrorRow
import com.swent.shifter.ui.components.PersonChip
import com.swent.shifter.ui.components.PersonChipState
import com.swent.shifter.ui.components.RoundedCard
import com.swent.shifter.ui.components.days.DayWithoutMissions
import com.swent.shifter.ui.components.days.EventDayPicker
import com.swent.shifter.ui.components.days.EventDayStrip
import com.swent.shifter.ui.components.days.missionSlot
import com.swent.shifter.ui.components.days.rememberEventDayPicker
import com.swent.shifter.ui.components.time.rememberNow
import com.swent.shifter.ui.mission.AssignDialog
import com.swent.shifter.ui.theme.shifter_mutedText
import com.swent.shifter.ui.theme.shifter_success
import com.swent.shifter.ui.theme.shifter_successContainer
import com.swent.shifter.ui.theme.shifter_warning
import com.swent.shifter.ui.theme.shifter_warningContainer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object AutoScheduleTestTags {
  const val SCREEN = "AutoScheduleScreen"
  const val SUMMARY = "AutoScheduleSummary"
  const val NEW_ASSIGNMENTS = "AutoScheduleNewAssignments"
  const val MISSIONS_TO_FINISH = "AutoScheduleMissionsToFinish"
  const val LOADING = "AutoScheduleLoading"
  const val RELOADING = "AutoScheduleReloading"
  const val LOAD_ERROR = "AutoScheduleLoadError"
  const val RETRY = "AutoScheduleRetry"
  const val NOTHING_TO_PROPOSE = "AutoScheduleNothingToPropose"
  const val EMPTY_DAY = "AutoScheduleEmptyDay"
  const val SAVE_ERROR = "AutoScheduleSaveError"
  const val SAVE_RELOAD = "AutoScheduleSaveReload"
  const val SAVING = "AutoScheduleSaving"
  const val CANCEL = "AutoScheduleCancel"
  const val ACCEPT = "AutoScheduleAccept"

  fun mission(missionId: String) = "AutoScheduleMission_$missionId"

  fun count(missionId: String) = "AutoScheduleCount_$missionId"

  fun toFill(missionId: String) = "AutoScheduleToFill_$missionId"

  fun assign(missionId: String) = "AutoScheduleAssign_$missionId"

  fun toFillReason(missionId: String) = "AutoScheduleToFillReason_$missionId"

  fun assignedChip(missionId: String, index: Int) = "AutoScheduleAssignedChip_${missionId}_$index"

  fun proposedChip(missionId: String, index: Int) = "AutoScheduleProposedChip_${missionId}_$index"
}

/**
 * The proposed schedule of an event, read from [viewModel].
 *
 * @param clock moves today on, so the screen opens on the right day and passes 04:00 by itself.
 * @param onCancel called to leave the screen without saving.
 * @param onSaved called once the proposal is saved, with how many people it assigned, to leave the
 *   screen and say so.
 */
@Composable
fun AutoScheduleScreen(
    viewModel: AutoScheduleViewModel,
    clock: MinuteClock,
    onCancel: () -> Unit,
    onSaved: (assignments: Int) -> Unit,
) {
  val state by viewModel.uiState.collectAsState()
  val currentOnSaved by rememberUpdatedState(onSaved)
  LaunchedEffect(state.saved) { if (state.saved) currentOnSaved(state.savedAssignments) }
  AutoScheduleContent(
      state = state,
      now = rememberNow(clock),
      actions =
          AutoScheduleActions(
              onAccept = viewModel::accept,
              onCancel = onCancel,
              onRetry = viewModel::retry,
              onAssign = viewModel::openAssign,
              onToggleAssignee = viewModel::toggleAssignee,
              onRetryAssignees = viewModel::retryAssignees,
              onSaveAssignees = viewModel::saveAssignees,
              onDismissAssign = viewModel::dismissAssign,
          ),
  )
}

/**
 * Stateless proposed schedule of an event: what the proposal adds over the whole event, then the
 * missions of one day at a time, and the buttons to accept or cancel it. Only the day shown is kept
 * here, across a configuration change.
 *
 * The days are picked from a strip, as on the organizer's Overview: it opens on today (the first
 * day before the event, the last one after it), and an event of one day has no strip.
 *
 * A mission still short of people offers to finish it by hand, in the assign dialog shown while
 * [AutoScheduleUiState.assigning] is set.
 *
 * @param now when the screen is read, which decides which day is today.
 * @param zone the time zone the days are counted in, the device's one in the app.
 */
@Composable
fun AutoScheduleContent(
    state: AutoScheduleUiState,
    actions: AutoScheduleActions,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
) {
  val starts = state.missions.map { it.startAt }
  val dayPicker = rememberEventDayPicker(state.eventPeriod, starts, now, zone)
  // An event without days read yet is still accepted as a whole: "Accept", not "Accept all 0 days".
  val dayCount = dayPicker.days.size.coerceAtLeast(1)
  Column(modifier = Modifier.fillMaxSize().testTag(AutoScheduleTestTags.SCREEN)) {
    Column(
        modifier =
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      when {
        state.missions.isEmpty() && state.isLoading ->
            CircularProgressIndicator(
                modifier =
                    Modifier.align(Alignment.CenterHorizontally)
                        .size(24.dp)
                        .testTag(AutoScheduleTestTags.LOADING),
                strokeWidth = 2.dp,
            )
        state.missions.isEmpty() && state.loadFailed -> LoadError(actions.onRetry)
        state.nothingToPropose -> NothingToPropose()
        else -> {
          // A reload, or one that failed, keeps the proposal already shown under it.
          if (state.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().testTag(AutoScheduleTestTags.RELOADING)
            )
          }
          if (state.loadFailed) LoadError(actions.onRetry)
          Proposal(state, starts, dayPicker, dayCount, zone, actions.onAssign)
        }
      }
    }
    BottomBar(state, actions, dayCount)
  }
  state.assigning?.let { assigning ->
    AssignDialog(
        assigning = assigning,
        onToggle = actions.onToggleAssignee,
        onRetry = actions.onRetryAssignees,
        onSave = actions.onSaveAssignees,
        onDismiss = actions.onDismissAssign,
    )
  }
}

/**
 * The summary, the day strip and the missions of the day shown. The strip marks every day with a
 * mission, as on the Overview, since a day can have missions to finish by hand without anyone
 * proposed.
 */
@Composable
private fun Proposal(
    state: AutoScheduleUiState,
    starts: List<Instant>,
    dayPicker: EventDayPicker,
    dayCount: Int,
    zone: ZoneId,
    onAssign: (String) -> Unit,
) {
  Summary(state, dayCount)
  if (dayPicker.showsStrip) {
    EventDayStrip(dayPicker, dayPicker.daysOf(starts))
  }
  val missions = state.missions.filter { dayPicker.isShown(it.startAt) }
  if (missions.isEmpty()) {
    DayWithoutMissions(dayPicker.shown, Modifier.testTag(AutoScheduleTestTags.EMPTY_DAY))
  }
  // Without a strip nothing says which day is shown: the date then goes on the cards of any other
  // day than today, as on the Overview.
  val day = if (dayPicker.showsStrip) dayPicker.shown else dayPicker.today
  missions.forEach { MissionCard(it, day, zone, canAssign = !state.isSaving, onAssign) }
}

/** What the proposal adds over the whole event, what stays to do by hand, and the legend. */
@Composable
private fun Summary(state: AutoScheduleUiState, dayCount: Int) {
  RoundedCard(Modifier.testTag(AutoScheduleTestTags.SUMMARY)) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Total(
            count = state.newAssignments,
            label =
                stringResource(
                    R.string.schedule_new_assignments_scope,
                    pluralStringResource(R.plurals.schedule_new_assignments, state.newAssignments),
                    pluralStringResource(R.plurals.schedule_all_days, dayCount, dayCount),
                ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).testTag(AutoScheduleTestTags.NEW_ASSIGNMENTS),
        )
        Total(
            count = state.missionsToFinish,
            label =
                pluralStringResource(
                    R.plurals.schedule_missions_to_finish,
                    state.missionsToFinish,
                ),
            color = shifter_warning,
            modifier = Modifier.weight(1f).testTag(AutoScheduleTestTags.MISSIONS_TO_FINISH),
        )
      }
      Legend()
      Text(
          text = stringResource(R.string.schedule_nothing_saved),
          style = MaterialTheme.typography.labelSmall,
          color = shifter_mutedText,
      )
    }
  }
}

/** A number of the summary, in large, above what it counts, read as one: "9, new assignments". */
@Composable
private fun Total(count: Int, label: String, color: Color, modifier: Modifier) {
  Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
    Text(text = count.toString(), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/**
 * What the two kinds of chips mean, drawn with the chips themselves, of one initial. Screen readers
 * skip it: it only explains the colours, and each chip of a mission already says its state.
 */
@Composable
private fun Legend() {
  Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.clearAndSetSemantics {},
  ) {
    PersonChip(
        stringResource(R.string.schedule_legend_proposed),
        PersonChipState.PROPOSED,
        maxInitials = 1,
    )
    PersonChip(
        stringResource(R.string.schedule_legend_assigned),
        PersonChipState.ASSIGNED,
        maxInitials = 1,
    )
  }
}

/**
 * A mission of the [day] shown: its title, slot and team, how many people it has once the proposal
 * is accepted, then who is already on it, in grey, and who the proposal adds, in blue. A mission
 * still short of people has its count and border in amber, and screen readers say how many are
 * missing. The slot is dated when the mission starts on another date than [day], as one after
 * midnight listed under the evening before. A short mission ends with what is left to fill by hand,
 * with Assign, and why nobody fills it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MissionCard(
    mission: ProposedMission,
    day: LocalDate,
    zone: ZoneId,
    canAssign: Boolean,
    onAssign: (String) -> Unit,
) {
  val short = mission.missing > 0
  val slot = missionSlot(mission.startAt, mission.endAt, day, zone)
  RoundedCard(
      modifier = Modifier.testTag(AutoScheduleTestTags.mission(mission.id)),
      borderColor = if (short) shifter_warning else MaterialTheme.colorScheme.outline,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
              text = mission.title,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
              text =
                  stringResource(
                      R.string.schedule_slot_team,
                      slot,
                      when {
                        mission.isGeneral -> stringResource(R.string.schedule_team_general)
                        else -> mission.teamName ?: stringResource(R.string.schedule_team_unknown)
                      },
                  ),
              style = MaterialTheme.typography.labelSmall,
              color = shifter_mutedText,
          )
        }
        val filled = mission.assigned.size + mission.proposed.size
        val people =
            pluralStringResource(
                R.plurals.schedule_count_description,
                mission.volunteersNeeded,
                filled,
                mission.volunteersNeeded,
            )
        // Read as "2 of 4 people, 2 to fill by hand" rather than "2 slash 4", and not only shown
        // by the colour.
        val description =
            if (short) stringResource(R.string.schedule_count_to_fill, people, mission.missing)
            else people
        Text(
            text = stringResource(R.string.schedule_count, filled, mission.volunteersNeeded),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (short) shifter_warning else shifter_success,
            modifier =
                Modifier.semantics { contentDescription = description }
                    .testTag(AutoScheduleTestTags.count(mission.id)),
        )
      }
      if (mission.assigned.isNotEmpty() || mission.proposed.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          // Those already assigned first: they stay, and the proposal only adds to them.
          mission.assigned.forEachIndexed { index, name ->
            PersonChip(
                name = name,
                state = PersonChipState.ASSIGNED,
                modifier = Modifier.testTag(AutoScheduleTestTags.assignedChip(mission.id, index)),
            )
          }
          mission.proposed.forEachIndexed { index, name ->
            PersonChip(
                name = name,
                state = PersonChipState.PROPOSED,
                modifier = Modifier.testTag(AutoScheduleTestTags.proposedChip(mission.id, index)),
            )
          }
        }
      }
      if (short) ToFillByHand(mission, slot, canAssign, onAssign)
    }
  }
}

/**
 * What a short mission still needs, "2 spots to fill by hand", with Assign to finish it, then why
 * nobody fills it: a General mission never is, nor one already started, and a team mission had
 * nobody else free. A mission whose team no longer exists has no Assign: there is nobody to pick.
 * Assign is off while the proposal is saved, so a hand pick can't make that save outdated.
 */
@Composable
private fun ToFillByHand(
    mission: ProposedMission,
    slot: String,
    canAssign: Boolean,
    onAssign: (String) -> Unit,
) {
  val teamGone = !mission.isGeneral && mission.teamName == null
  val assignDescription = stringResource(R.string.schedule_assign_description, mission.title)
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(shifter_warningContainer)
                .padding(start = 12.dp)
                .testTag(AutoScheduleTestTags.toFill(mission.id)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text = pluralStringResource(R.plurals.schedule_to_fill, mission.missing, mission.missing),
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = shifter_warning,
          modifier = Modifier.weight(1f),
      )
      if (!teamGone) {
        TextButton(
            onClick = { onAssign(mission.id) },
            enabled = canAssign,
            modifier =
                Modifier.semantics { contentDescription = assignDescription }
                    .testTag(AutoScheduleTestTags.assign(mission.id)),
        ) {
          Text(stringResource(R.string.schedule_assign), fontWeight = FontWeight.SemiBold)
          Icon(
              Icons.AutoMirrored.Filled.KeyboardArrowRight,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
          )
        }
      }
    }
    Text(
        text =
            when {
              mission.isGeneral -> stringResource(R.string.schedule_to_fill_general)
              mission.started -> stringResource(R.string.schedule_to_fill_started)
              mission.teamName == null -> stringResource(R.string.schedule_to_fill_team_gone)
              else -> stringResource(R.string.schedule_to_fill_team, mission.teamName, slot)
            },
        style = MaterialTheme.typography.labelSmall,
        color = shifter_mutedText,
        modifier = Modifier.testTag(AutoScheduleTestTags.toFillReason(mission.id)),
    )
  }
}

/** Why there is nothing to accept: every mission is full, or nobody fits the ones left. */
@Composable
private fun NothingToPropose() {
  RoundedCard(Modifier.testTag(AutoScheduleTestTags.NOTHING_TO_PROPOSE)) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Box(
          modifier = Modifier.size(48.dp).clip(CircleShape).background(shifter_successContainer),
          contentAlignment = Alignment.Center,
      ) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = shifter_success)
      }
      Text(
          text = stringResource(R.string.schedule_nothing_title),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
          text = stringResource(R.string.schedule_nothing_text),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
      )
    }
  }
}

/** Why the proposal could not be prepared, with Retry. */
@Composable
private fun LoadError(onRetry: () -> Unit) {
  LoadErrorRow(
      message = stringResource(R.string.schedule_load_failed),
      retryLabel = stringResource(R.string.schedule_retry),
      onRetry = onRetry,
      messageTag = AutoScheduleTestTags.LOAD_ERROR,
      retryTag = AutoScheduleTestTags.RETRY,
  )
}

/**
 * Why the last save failed, if it did, then Cancel and Accept for every day at once. Accept is on
 * only when [AutoScheduleUiState.canAccept], and shows a spinner while the proposal is saved.
 */
@Composable
private fun BottomBar(state: AutoScheduleUiState, actions: AutoScheduleActions, dayCount: Int) {
  Column {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      state.saveError?.let { SaveError(it, actions.onRetry) }
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = actions.onCancel,
            enabled = !state.isSaving,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f).height(52.dp).testTag(AutoScheduleTestTags.CANCEL),
        ) {
          Text(stringResource(R.string.schedule_cancel), fontWeight = FontWeight.SemiBold)
        }
        Button(
            onClick = actions.onAccept,
            enabled = state.canAccept,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f).height(52.dp).testTag(AutoScheduleTestTags.ACCEPT),
        ) {
          if (state.isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp).testTag(AutoScheduleTestTags.SAVING),
                strokeWidth = 2.dp,
            )
          } else {
            Text(
                text = pluralStringResource(R.plurals.schedule_accept, dayCount, dayCount),
                fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }
    }
  }
}

/**
 * Why saving failed: an outdated proposal is prepared again from here, since accepting it again
 * would fail the same way.
 */
@Composable
private fun SaveError(error: ScheduleSaveError, onReload: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
        text =
            stringResource(
                when (error) {
                  ScheduleSaveError.RETRY -> R.string.schedule_save_failed
                  ScheduleSaveError.OUTDATED -> R.string.schedule_save_outdated
                  ScheduleSaveError.TOO_LARGE -> R.string.schedule_save_too_large
                }
            ),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.weight(1f).testTag(AutoScheduleTestTags.SAVE_ERROR),
    )
    if (error == ScheduleSaveError.OUTDATED) {
      TextButton(
          onClick = onReload,
          modifier = Modifier.testTag(AutoScheduleTestTags.SAVE_RELOAD),
      ) {
        Text(stringResource(R.string.schedule_reload))
      }
    }
  }
}
