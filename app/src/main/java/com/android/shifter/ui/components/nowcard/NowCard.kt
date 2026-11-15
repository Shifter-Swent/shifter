// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.nowcard

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.ui.checkin.NotCheckedIn
import com.swent.shifter.ui.components.PrimaryButton
import com.swent.shifter.ui.components.time.formatDuration
import com.swent.shifter.ui.components.time.formatTime
import com.swent.shifter.ui.components.time.minutesLeft
import com.swent.shifter.ui.theme.shifter_success
import com.swent.shifter.ui.theme.shifter_warning
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * The volunteer's now card: what to do now and what comes next, in each [NowState]. It replaces the
 * automatic check-in card, so it also says when the volunteer was checked in, and why not yet.
 *
 * @param state the volunteer's state, from today's missions only (see [nowState]).
 * @param now the clock's tick, which the countdowns ("In 20 min", "40 min left") are read at.
 * @param checkedInAt when the volunteer was checked in to [NowState.Ongoing.current], or early to
 *   [NowState.Soon.next], from the check-in records; null when unknown or not checked in.
 * @param notCheckedIn why the volunteer is not checked in to [NowState.Late.current], as the
 *   automatic check-in knows it (`whyNotCheckedIn`); a mission without a place is told by the card.
 * @param onOpenMission shows a mission: on the map when it has a place, its details otherwise.
 * @param onContact contacts a mission's manager, or the organizer for a mission of no team.
 * @param footer under the card, outside its animations: the location permission gate, whose state
 *   must survive the card changing state.
 */
@Composable
fun NowCard(
    state: NowState,
    now: Instant,
    checkedInAt: Instant?,
    notCheckedIn: NotCheckedIn?,
    onOpenMission: (Mission) -> Unit,
    onContact: (Mission) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    footer: @Composable () -> Unit = {},
) {
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    NowCardContainer(
        tone = state.tone,
        status = stringResource(state.status),
        headline = headline(state, now, zone),
        state = NowCardBody(state, now, checkedInAt, notCheckedIn),
        strip = { body -> NextStrip(body, onOpenMission) },
    ) { body ->
      Body(body, zone, onOpenMission, onContact)
    }
    footer()
  }
}

/** What the body draws, kept whole by the content sliding out when the state changes. */
private data class NowCardBody(
    val state: NowState,
    val now: Instant,
    val checkedInAt: Instant?,
    val notCheckedIn: NotCheckedIn?,
)

private val NowState.tone: NowTone
  get() =
      when (this) {
        is NowState.Soon -> NowTone.SOON
        is NowState.Late -> NowTone.ALERT
        is NowState.Ongoing -> NowTone.LIVE
        is NowState.Later,
        NowState.DoneForToday -> NowTone.CALM
      }

@get:StringRes
private val NowState.status: Int
  get() =
      when (this) {
        is NowState.Later -> R.string.now_card_next_mission
        is NowState.Soon -> R.string.now_card_starting_soon
        is NowState.Late -> R.string.now_card_not_checked_in
        is NowState.Ongoing -> R.string.now_card_now
        NowState.DoneForToday -> R.string.now_card_today
      }

@Composable
private fun headline(state: NowState, now: Instant, zone: ZoneId): String =
    when (state) {
      is NowState.Later -> stringResource(R.string.now_card_at, state.next.startAt.formatTime(zone))
      is NowState.Soon ->
          stringResource(
              R.string.now_card_in,
              // At least a minute: Soon until the start, even if read at a later tick.
              formatDuration(maxOf(minutesLeft(now, state.next.startAt), Duration.ofMinutes(1))),
          )
      is NowState.Late -> {
        val since = Duration.between(state.current.startAt, now)
        if (since.toMinutes() < 1) stringResource(R.string.now_card_started_just_now)
        else stringResource(R.string.now_card_started_ago, formatDuration(since))
      }
      is NowState.Ongoing ->
          stringResource(
              R.string.now_card_left,
              formatDuration(minutesLeft(now, state.current.endAt)),
          )
      NowState.DoneForToday -> stringResource(R.string.now_card_done_for_today)
    }

@Composable
private fun Body(
    body: NowCardBody,
    zone: ZoneId,
    onOpenMission: (Mission) -> Unit,
    onContact: (Mission) -> Unit,
) {
  when (val state = body.state) {
    is NowState.Later -> MissionSummary(state.next, zone)
    is NowState.Soon -> {
      MissionSummary(state.next, zone)
      CountdownBar(
          start = state.next.startAt,
          now = body.now,
          window = NowCardRules.SOON_BEFORE_START,
          color = NowTone.SOON.accent,
          zone = zone,
      )
      OpenAction(state.next, onOpenMission)
      // Checked in early, the volunteer no longer needs to be told how the check-in works.
      when {
        body.checkedInAt != null -> CheckedInLine(body.checkedInAt, zone)
        state.next.location != null -> Hint()
      }
    }
    is NowState.Late -> {
      MissionSummary(state.current, zone)
      TimeBar(state.current.startAt, state.current.endAt, body.now, NowTone.ALERT.accent, zone)
      ReasonLine(state.current, body.notCheckedIn)
      ContactAction(state.current, late = true, onContact)
    }
    is NowState.Ongoing -> {
      MissionSummary(state.current, zone)
      TimeBar(state.current.startAt, state.current.endAt, body.now, NowTone.LIVE.accent, zone)
      body.checkedInAt?.let { CheckedInLine(it, zone) }
      // One action at a time: the next mission's strip takes it when there is one.
      if (state.next == null) ContactAction(state.current, late = false, onContact)
    }
    NowState.DoneForToday -> Unit
  }
}

/**
 * The next mission under a running one. It takes the card's action when the volunteer is checked
 * in; when they are late, telling their manager stays the one action.
 */
@Composable
private fun NextStrip(body: NowCardBody, onOpenMission: (Mission) -> Unit) {
  val (next, late) =
      when (val state = body.state) {
        is NowState.Ongoing -> state.next to false
        is NowState.Late -> state.next to true
        else -> null to false
      }
  next ?: return
  ThenStrip(
      startsIn = minutesLeft(body.now, next.startAt),
      title = next.title,
      place = next.location?.address,
      action = if (late) null else ({ OpenAction(next, onOpenMission) }),
  )
}

/** The mission's title, then its hours and its place. */
@Composable
private fun MissionSummary(mission: Mission, zone: ZoneId) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
        text = mission.title,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 15.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      Detail(
          stringResource(
              R.string.now_card_time_range,
              mission.startAt.formatTime(zone),
              mission.endAt.formatTime(zone),
          )
      )
      mission.location?.let {
        Spacer(Modifier.width(12.dp))
        Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Detail(it.address)
      }
    }
  }
}

@Composable
private fun Detail(text: String) {
  Text(
      text = text,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontSize = 13.sp,
      lineHeight = 17.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
  )
}

/** Why the volunteer is not checked in to [mission] yet, when the card knows it. */
@Composable
private fun ReasonLine(mission: Mission, reason: NotCheckedIn?) {
  val message =
      when {
        mission.location != null -> reason?.message
        mission.teamId == null -> R.string.now_card_no_place_organizer
        else -> R.string.now_card_no_place
      }
  message ?: return
  Text(
      text = stringResource(message),
      color = shifter_warning,
      fontSize = 13.sp,
      lineHeight = 17.sp,
      // Announced when it appears or changes while the card stays late.
      modifier =
          Modifier.testTag(NowCardTestTags.REASON).semantics { liveRegion = LiveRegionMode.Polite },
  )
}

@get:StringRes
private val NotCheckedIn.message: Int
  get() =
      when (this) {
        NotCheckedIn.OUTSIDE_ZONE -> R.string.now_card_outside_zone
        NotCheckedIn.REFUSED -> R.string.auto_check_in_not_allowed
        NotCheckedIn.OFFLINE -> R.string.auto_check_in_offline
        NotCheckedIn.UNEXPECTED -> R.string.auto_check_in_unexpected
      }

@Composable
private fun CheckedInLine(checkedInAt: Instant, zone: ZoneId) {
  Text(
      text = stringResource(R.string.now_card_checked_in_at, checkedInAt.formatTime(zone)),
      color = shifter_success,
      fontSize = 13.sp,
      lineHeight = 17.sp,
  )
}

/** How the volunteer is checked in to a mission with a place. */
@Composable
private fun Hint() {
  Text(
      text = stringResource(R.string.auto_check_in_explanation),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontSize = 12.sp,
      lineHeight = 16.sp,
  )
}

/** Shows [mission]: on the map when it has a place, its details otherwise. */
@Composable
private fun OpenAction(mission: Mission, onOpenMission: (Mission) -> Unit) {
  val onMap = mission.location != null
  PrimaryButton(
      text =
          stringResource(
              if (onMap) R.string.now_card_view_on_map else R.string.now_card_view_details
          ),
      onClick = { onOpenMission(mission) },
      testTag = NowCardTestTags.ACTION,
      icon = if (onMap) Icons.Filled.Map else Icons.Filled.Info,
  )
}

/**
 * Contacts the manager of [mission], or the organizer for a mission of no team: in amber when the
 * volunteer is [late], quietly otherwise.
 */
@Composable
private fun ContactAction(mission: Mission, late: Boolean, onContact: (Mission) -> Unit) {
  val organizer = mission.teamId == null
  val text =
      when {
        late && organizer -> R.string.now_card_tell_organizer
        late -> R.string.now_card_tell_manager
        organizer -> R.string.now_card_contact_organizer
        else -> R.string.now_card_contact_manager
      }
  val colors =
      if (late) {
        ButtonDefaults.buttonColors(
            containerColor = shifter_warning,
            contentColor = MaterialTheme.colorScheme.surface,
        )
      } else {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
      }
  PrimaryButton(
      text = stringResource(text),
      onClick = { onContact(mission) },
      testTag = NowCardTestTags.ACTION,
      icon = Icons.AutoMirrored.Filled.Chat,
      colors = colors,
  )
}
