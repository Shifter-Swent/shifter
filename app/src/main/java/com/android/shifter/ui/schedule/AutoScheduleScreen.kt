// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.model.time.formatTime
import com.swent.shifter.ui.components.PersonChip
import com.swent.shifter.ui.components.PersonChipState
import com.swent.shifter.ui.components.days.DayWithoutMissions
import com.swent.shifter.ui.components.days.EventDayPicker
import com.swent.shifter.ui.components.days.EventDayStrip
import com.swent.shifter.ui.components.days.rememberEventDayPicker
import com.swent.shifter.ui.theme.shifter_mutedText
import com.swent.shifter.ui.theme.shifter_success
import com.swent.shifter.ui.theme.shifter_successContainer
import com.swent.shifter.ui.theme.shifter_warning
import java.time.Instant
import java.time.ZoneId

object AutoScheduleTestTags {
  const val SCREEN = "AutoScheduleScreen"
  const val SUMMARY = "AutoScheduleSummary"
  const val NEW_ASSIGNMENTS = "AutoScheduleNewAssignments"
  const val MISSIONS_TO_FINISH = "AutoScheduleMissionsToFinish"
  const val LOADING = "AutoScheduleLoading"
  const val LOAD_ERROR = "AutoScheduleLoadError"
  const val RETRY = "AutoScheduleRetry"
  const val NOTHING_TO_PROPOSE = "AutoScheduleNothingToPropose"
  const val EMPTY_DAY = "AutoScheduleEmptyDay"
  const val SAVE_ERROR = "AutoScheduleSaveError"
  const val CANCEL = "AutoScheduleCancel"
  const val ACCEPT = "AutoScheduleAccept"

  fun mission(missionId: String) = "AutoScheduleMission_$missionId"

  fun count(missionId: String) = "AutoScheduleCount_$missionId"

  fun assignedChip(missionId: String, index: Int) = "AutoScheduleAssignedChip_${missionId}_$index"

  fun proposedChip(missionId: String, index: Int) = "AutoScheduleProposedChip_${missionId}_$index"
}

/**
 * Stateless proposed schedule of an event: what the proposal adds over the whole event, then the
 * missions of one day at a time, and the buttons to accept or cancel it. Only the day shown is kept
 * here, across a configuration change.
 *
 * The days are picked from a strip, as on the organizer's Overview: it opens on today (the first
 * day before the event, the last one after it), and an event of one day has no strip.
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
  val dayPicker =
      rememberEventDayPicker(state.eventPeriod, state.missions.map { it.startAt }, now, zone)
  // An event without days read yet is still accepted as a whole: "Accept", not "Accept all 0 days".
  val dayCount = dayPicker.days.size.coerceAtLeast(1)
  Column(modifier = Modifier.fillMaxSize().testTag(AutoScheduleTestTags.SCREEN)) {
    Column(
        modifier =
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      when {
        state.isLoading && state.missions.isEmpty() ->
            CircularProgressIndicator(
                modifier =
                    Modifier.align(Alignment.CenterHorizontally)
                        .size(24.dp)
                        .testTag(AutoScheduleTestTags.LOADING),
                strokeWidth = 2.dp,
            )
        state.loadFailed -> LoadError(actions.onRetry)
        state.nothingToPropose -> NothingToPropose()
        else -> Proposal(state, dayPicker, dayCount, zone)
      }
    }
    BottomBar(state, actions, dayCount)
  }
}

/** The summary, the legend, the day strip and the missions of the day shown. */
@Composable
private fun Proposal(
    state: AutoScheduleUiState,
    dayPicker: EventDayPicker,
    dayCount: Int,
    zone: ZoneId,
) {
  Summary(state, dayCount)
  Legend()
  if (dayPicker.showsStrip) {
    EventDayStrip(dayPicker, dayPicker.daysOf(state.missions.map { it.startAt }))
  }
  val missions = state.missions.filter { dayPicker.isShown(it.startAt) }
  if (missions.isEmpty()) {
    DayWithoutMissions(dayPicker.shown, Modifier.testTag(AutoScheduleTestTags.EMPTY_DAY))
  }
  missions.forEach { MissionCard(it, zone) }
}

/** What the proposal adds over the whole event, and what stays to do by hand. */
@Composable
private fun Summary(state: AutoScheduleUiState, dayCount: Int) {
  ScheduleCard(Modifier.testTag(AutoScheduleTestTags.SUMMARY)) {
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
      Text(
          text = stringResource(R.string.schedule_nothing_saved),
          style = MaterialTheme.typography.labelSmall,
          color = shifter_mutedText,
      )
    }
  }
}

/** A number of the summary, in large, above what it counts. */
@Composable
private fun Total(count: Int, label: String, color: Color, modifier: Modifier) {
  Column(modifier = modifier) {
    Text(text = count.toString(), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** What the two kinds of chips mean, drawn with the chips themselves, of one initial. */
@Composable
private fun Legend() {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
 * A mission of the day shown: its title, slot and team, how many people it has once the proposal is
 * accepted, then who is already on it, in grey, and who the proposal adds, in blue.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MissionCard(mission: ProposedMission, zone: ZoneId) {
  ScheduleCard(Modifier.testTag(AutoScheduleTestTags.mission(mission.id))) {
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
                      stringResource(
                          R.string.schedule_slot,
                          mission.startAt.formatTime(zone),
                          mission.endAt.formatTime(zone),
                      ),
                      mission.teamName ?: stringResource(R.string.schedule_team_general),
                  ),
              style = MaterialTheme.typography.labelSmall,
              color = shifter_mutedText,
          )
        }
        val filled = mission.assigned.size + mission.proposed.size
        Text(
            text = stringResource(R.string.schedule_count, filled, mission.volunteersNeeded),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (mission.missing > 0) shifter_warning else shifter_success,
            modifier = Modifier.testTag(AutoScheduleTestTags.count(mission.id)),
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
    }
  }
}

/** Why there is nothing to accept: every mission is full, or nobody fits the ones left. */
@Composable
private fun NothingToPropose() {
  ScheduleCard(Modifier.testTag(AutoScheduleTestTags.NOTHING_TO_PROPOSE)) {
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

@Composable
private fun LoadError(onRetry: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
        text = stringResource(R.string.schedule_load_failed),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.weight(1f).testTag(AutoScheduleTestTags.LOAD_ERROR),
    )
    TextButton(onClick = onRetry, modifier = Modifier.testTag(AutoScheduleTestTags.RETRY)) {
      Text(stringResource(R.string.schedule_retry))
    }
  }
}

/**
 * Cancel, and Accept for every day at once. Accept is off while there is nothing to accept, and
 * while the proposal is prepared or saved.
 */
@Composable
private fun BottomBar(state: AutoScheduleUiState, actions: AutoScheduleActions, dayCount: Int) {
  Column {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (state.saveFailed) {
        Text(
            text = stringResource(R.string.schedule_save_failed),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag(AutoScheduleTestTags.SAVE_ERROR),
        )
      }
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
            enabled =
                !state.isSaving && !state.isLoading && !state.loadFailed && !state.nothingToPropose,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f).height(52.dp).testTag(AutoScheduleTestTags.ACCEPT),
        ) {
          Text(
              text = pluralStringResource(R.plurals.schedule_accept, dayCount, dayCount),
              fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }
  }
}

/** A rounded, outlined block of the screen, as on the organizer's Overview. */
@Composable
private fun ScheduleCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  val shape = RoundedCornerShape(18.dp)
  Box(
      modifier =
          modifier
              .fillMaxWidth()
              .clip(shape)
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .border(1.dp, MaterialTheme.colorScheme.outline, shape)
              .padding(16.dp)
  ) {
    content()
  }
}
