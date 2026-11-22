// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.model.time.formatTime
import com.swent.shifter.ui.components.PrimaryButton
import com.swent.shifter.ui.components.avatarInitials
import com.swent.shifter.ui.components.nowcard.CountdownBar
import com.swent.shifter.ui.components.nowcard.NowCardContainer
import com.swent.shifter.ui.components.nowcard.NowCardTestTags
import com.swent.shifter.ui.components.nowcard.NowTone
import com.swent.shifter.ui.components.nowcard.ThenStrip
import com.swent.shifter.ui.components.nowcard.TimeBar
import com.swent.shifter.ui.components.nowcard.accent
import com.swent.shifter.ui.components.time.formatDuration
import com.swent.shifter.ui.components.time.minutesLeft
import com.swent.shifter.ui.theme.SatoshiFontFamily
import com.swent.shifter.ui.theme.shifter_avatarBackground
import com.swent.shifter.ui.theme.shifter_success
import com.swent.shifter.ui.theme.shifter_warning
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The tags of the manager's now card. The parts the shared shell draws keep its tags, rather than
 * being tagged a second time.
 */
object TeamNowCardTestTags {
  const val CARD = NowCardTestTags.CARD
  const val STATUS = NowCardTestTags.STATUS
  const val TIME_BAR = NowCardTestTags.TIME_BAR
  const val NEXT = NowCardTestTags.NEXT
  const val ACTION = NowCardTestTags.ACTION

  /**
   * The row of a volunteer the card reports missing from a mission. Keyed by both, as the mission
   * list's tags are: a volunteer missing from two missions at once has a row under each.
   */
  fun missing(missionId: String, userId: String) = "teamNowCardMissing_${missionId}_$userId"

  /** What checks them in to that mission: the row itself, so the whole row is the target. */
  fun checkIn(missionId: String, userId: String) = "teamNowCardCheckIn_${missionId}_$userId"

  /** The row of a volunteer kept a moment in green after a manual check-in. */
  fun justCheckedIn(missionId: String, userId: String) =
      "teamNowCardJustCheckedIn_${missionId}_$userId"
}

/**
 * How long a volunteer stays in green after the manager checks them in: long enough that the row
 * does not leave under the finger that tapped it, short enough to be gone at the next glance.
 */
internal const val JUST_CHECKED_IN_MILLIS = 5_000L

/**
 * The manager's now card, in each state of their team: who is missing and the one tap that checks
 * them in, how many are at work and what comes next, who is expected on the mission about to start
 * or just started, what the team does next, or that it is done.
 *
 * The card draws the state it is given and reports what the manager does. It holds no attendance of
 * its own, apart from who was just checked in, whom the state stops reporting as soon as the write
 * is pending: a volunteer it goes on reporting is drawn as missing, so a check-in that did not
 * happen is never confirmed in green. Checking the last one in turns the team's state to
 * [TeamNowState.Running], or to [TeamNowState.Arriving] when another mission is at its handover,
 * and the card moves to it with their confirmation still on it. Missions whose attendance is not
 * known are said to be unknown rather than counted as nobody there.
 *
 * @param now the clock's tick, which the lateness, the time bars and the countdown are read at.
 * @param onCheckIn checks a volunteer in by hand, on the mission their row belongs to.
 * @param onViewOnMap shows the team on the map, the card's only action.
 */
@Composable
fun TeamNowCard(
    state: TeamNowState,
    now: Instant,
    onCheckIn: (missionId: String, userId: String) -> Unit,
    onViewOnMap: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
  // Who the manager just checked in, kept by the card rather than by the state: the state stops
  // reporting them at once, and the card holds them a moment so the tap is acknowledged. A list
  // of rows, not a map of volunteers: the rows keep the order they were checked in, and the same
  // person missing from two missions at once is checked in to one without the other following.
  val justCheckedIn = remember { mutableStateListOf<PendingCheckIn>() }
  val scope = rememberCoroutineScope()
  val checkIn: (MissingAssignee) -> Unit = { assignee ->
    // Every tap is forwarded: whether a check-in can be made is the view model's to say, and a
    // second one on a volunteer it has already checked in is left alone there.
    onCheckIn(assignee.mission.id, assignee.volunteer.userId)
    // One row however many taps, and until a moment after the last of them: a check-in the view
    // model refuses leaves the row reported, hence tappable again, and that tap must not show a
    // second row or be cut short by the first tap's timer.
    val waiting = justCheckedIn.firstOrNull { it.assignee.rowId == assignee.rowId }
    if (waiting == null) justCheckedIn += PendingCheckIn(assignee) else waiting.taps++
    scope.launch {
      delay(JUST_CHECKED_IN_MILLIS.milliseconds)
      val pending = justCheckedIn.firstOrNull { it.assignee.rowId == assignee.rowId }
      if (pending != null && --pending.taps == 0) justCheckedIn.remove(pending)
    }
  }

  // Nobody is reported missing once the team is Running, so a row the card still holds is then a
  // check-in that happened, and keeps its moment in green on the state the card has moved to.
  val missing = (state as? TeamNowState.Missing)?.missing ?: emptyList()
  val reported = missing.mapTo(mutableSetOf()) { it.rowId }
  // Green is the check-in that happened, which is the row the state has let go of. One it still
  // reports is still missing -- the view model refused the check-in, or the manager undid it
  // before the moment was over -- and stays the missing row it is, its tap included.
  val left = justCheckedIn.map { it.assignee }.filter { it.rowId !in reported }
  // The rows the state has let go of are kept where the eye can still find them: after the rest.
  val rows =
      missing.map { TeamNowRow(it, justCheckedIn = false) } +
          left.map { TeamNowRow(it, justCheckedIn = true) }

  val shown = teamNowCardBody(state, rows, now, zone)
  NowCardContainer(
      tone = shown.tone,
      status = shown.status,
      headline = shown.headline,
      state = shown,
      modifier = modifier,
      strip = { body -> body.next?.let { NextStrip(it, body.now) } },
  ) { body ->
    body.detail?.let { Line(it) }
    body.missions.forEach { mission ->
      if (mission.startAt > body.now) {
        CountdownBar(
            start = mission.startAt,
            now = body.now,
            window = TeamNowRules.UPCOMING_WINDOW,
            color = NowTone.SOON.accent,
            zone = zone,
        )
      } else {
        TimeBar(
            from = mission.startAt,
            to = mission.endAt,
            now = body.now,
            // The bar says the mission is running, whatever the card's tone says of its team.
            color = NowTone.LIVE.accent,
            zone = zone,
            // The headline of a card listing assignees already names their mission.
            title = if (body.assignees.isEmpty()) mission.title else null,
        )
      }
    }
    body.rows.forEach { row -> VolunteerRow(row, body.now, checkIn) }
    body.assignees.forEach { AssigneeRow(it, body.attendanceKnown) }
    if (body.short > 0) {
      Detail(
          pluralStringResource(R.plurals.attendance_now_short_of, body.short, body.short),
          shifter_warning,
      )
    }
    if (body.others > 0) {
      Line(pluralStringResource(R.plurals.attendance_now_others, body.others, body.others))
    }
    if (body.attendanceUnknown.isNotEmpty()) {
      val unknown = body.attendanceUnknown.size
      Line(pluralStringResource(R.plurals.attendance_now_unknown, unknown, unknown))
    }
    // The map shows the team on its mission, so a card speaking for none leaves it out.
    if (body.missions.isNotEmpty()) {
      val live = body.tone == NowTone.LIVE
      PrimaryButton(
          text = stringResource(R.string.attendance_now_view_on_map),
          onClick = onViewOnMap,
          testTag = TeamNowCardTestTags.ACTION,
          icon = Icons.Filled.Map,
          colors =
              ButtonDefaults.buttonColors(
                  // The card's own color, except at work where the design draws it dark.
                  containerColor =
                      if (live) MaterialTheme.colorScheme.surface else body.tone.accent,
                  contentColor =
                      if (live) MaterialTheme.colorScheme.onSurface
                      else MaterialTheme.colorScheme.surface,
              ),
      )
    }
  }
}

/**
 * What the card says for [state]. [rows] are the volunteers it draws of its own, missing or just
 * checked in; [zone] is the one the hours are read in.
 */
@Composable
private fun teamNowCardBody(
    state: TeamNowState,
    rows: List<TeamNowRow>,
    now: Instant,
    zone: ZoneId,
): TeamNowCardBody =
    when (state) {
      is TeamNowState.Missing -> {
        // "N volunteers missing" counts people, not rows: one volunteer missing from two missions
        // is one person to find. It counts what the state reports, so a volunteer just checked in
        // is shown in green without the count reading zero before the state has caught up.
        val count = state.missing.distinctBy { it.volunteer.userId }.size
        // onDuty leaves out everyone the state still reports missing, so only a volunteer it has
        // dropped from every mission is one of the others, and each of them once however many
        // rows they are shown in.
        val stillMissing = state.missing.mapTo(mutableSetOf()) { it.volunteer.userId }
        val shownPresent =
            rows
                .filter { it.justCheckedIn }
                .mapTo(mutableSetOf()) { it.assignee.volunteer.userId } - stillMissing
        TeamNowCardBody(
            tone = NowTone.ALERT,
            status = stringResource(R.string.attendance_now_needs_you),
            headline = pluralStringResource(R.plurals.attendance_now_missing, count, count),
            rows = rows,
            // The missions of the rows drawn, not of those reported: a mission whose last missing
            // volunteer was just checked in keeps its bar while their row is still there, and a
            // mission with two rows has one bar all the same.
            missions = rows.map { it.assignee.mission }.distinctBy { it.id },
            // A volunteer shown in green is a row of their own, so not one of the others.
            others = (state.onDuty - shownPresent.size).coerceAtLeast(0),
            attendanceUnknown = state.attendanceUnknown,
            next = null,
            now = now,
        )
      }
      is TeamNowState.Running -> {
        // "All on duty" speaks for everyone the missions ask for, so it is only said when every
        // ongoing mission's attendance is known, somebody is on duty, and no mission is short of
        // the volunteers it needs: all of its assignees can be there and still leave it short,
        // which the mission list flags as understaffed. Otherwise the card keeps the plain "Now".
        val allOnDuty =
            state.attendanceUnknown.isEmpty() &&
                state.onDuty > 0 &&
                state.ongoing.none { it.isUnderstaffed }
        TeamNowCardBody(
            tone = NowTone.LIVE,
            status =
                stringResource(
                    if (allOnDuty) R.string.attendance_now_all_on_duty else R.string.now_card_now
                ),
            headline =
                // Nobody is counted at work without having been seen there: a mission whose
                // attendance nobody has read yet, as on the first load, and one nobody is
                // assigned to both report nobody, so the missions are counted instead of "0 at
                // work" reading as a team that has not come.
                if (state.onDuty > 0) {
                  stringResource(R.string.attendance_now_at_work, state.onDuty)
                } else {
                  val running = state.ongoing.size
                  pluralStringResource(R.plurals.attendance_now_running, running, running)
                },
            // Only those just checked in: this state reports nobody missing.
            rows = rows,
            missions = state.ongoing,
            // The headline counts everyone at work, a green row's volunteer included, so there
            // are no others to count beside them.
            others = 0,
            attendanceUnknown = state.attendanceUnknown,
            // Only the first upcoming mission is shown when several start at the same time.
            // The card intentionally uses a single ThenStrip.
            next = state.next.firstOrNull(),
            now = now,
        )
      }
      is TeamNowState.Arriving -> {
        // The mission people are expected on: the handover, or the soonest one to come.
        val handover = state.handovers.firstOrNull()
        val mission = handover ?: state.next.first()
        val started = mission.startAt <= now
        TeamNowCardBody(
            tone = NowTone.SOON,
            status =
                stringResource(
                    if (started) R.string.attendance_now_starting_now
                    else R.string.now_card_starting_soon
                ),
            headline =
                if (started) stringResource(R.string.attendance_now_started, mission.title)
                else
                    stringResource(
                        R.string.attendance_now_in,
                        mission.title,
                        formatDuration(minutesLeft(now, mission.startAt)),
                    ),
            detail =
                mission.andPlace(
                    stringResource(
                        R.string.attendance_schedule,
                        mission.startAt.formatTime(zone),
                        mission.endAt.formatTime(zone),
                    )
                ),
            rows = rows,
            missions = listOf(mission),
            assignees = mission.assignees,
            // Nobody is short before the mission starts: those not there yet are expected, not
            // missing. Once it has, only a mission whose check-ins are in can be called short.
            short =
                if (started && mission.isUnderstaffed && !mission.attendanceFailed) {
                  mission.volunteersNeeded - mission.presentCount
                } else 0,
            attendanceUnknown = state.attendanceUnknown,
            // At a handover the missions to come are still to come, so the strip keeps the first
            // of them, as the card of a team at work does. Before a start, the mission shown is
            // that first one, which no strip repeats underneath it.
            next = if (handover != null) state.next.firstOrNull() else null,
            // Who is there is only known once the check-ins of the mission have arrived: while
            // they have not, nobody is shown present, so an assignee is neither said to have
            // arrived nor to be expected.
            attendanceKnown = mission.attendanceKnown,
            now = now,
        )
      }
      is TeamNowState.Quiet -> {
        val mission = state.next.first()
        val assigned = mission.assignees.size
        TeamNowCardBody(
            tone = NowTone.CALM,
            status = stringResource(R.string.attendance_now_next_for_team),
            headline =
                stringResource(
                    R.string.attendance_now_at_time,
                    mission.title,
                    mission.startAt.formatTime(zone),
                ),
            detail =
                mission.andPlace(
                    pluralStringResource(R.plurals.attendance_now_assigned, assigned, assigned)
                ),
            now = now,
        )
      }
      TeamNowState.Done ->
          TeamNowCardBody(
              tone = NowTone.CALM,
              status = stringResource(R.string.now_card_today),
              headline = stringResource(R.string.attendance_now_done),
              now = now,
          )
    }

/**
 * What the team does next, at the foot of the card: when it starts, and how many of the volunteers
 * it needs are there already. A mission whose check-in has not opened, or whose attendance has not
 * arrived, can have nobody present yet, so it says how many are expected rather than that none
 * arrived.
 */
@Composable
private fun NextStrip(next: AttendanceMission, now: Instant) {
  val needed = next.volunteersNeeded
  val arrived = next.status(now) == AttendanceStatus.EARLY
  val short = if (arrived) needed - next.presentCount else 0
  ThenStrip(
      startsIn = minutesLeft(now, next.startAt),
      title = next.title,
      place =
          if (arrived) stringResource(R.string.attendance_now_arrived, next.presentCount, needed)
          else pluralStringResource(R.plurals.attendance_now_expected, needed, needed),
      note =
          if (short > 0) {
            {
              Detail(
                  pluralStringResource(R.plurals.attendance_now_short_of, short, short),
                  shifter_warning,
              )
            }
          } else null,
  )
}

/** What the card draws, kept whole by the shell while a state slides away. */
private data class TeamNowCardBody(
    val tone: NowTone,
    val status: String,
    val headline: String,
    val rows: List<TeamNowRow> = emptyList(),
    val missions: List<AttendanceMission> = emptyList(),
    val others: Int = 0,
    val attendanceUnknown: List<AttendanceMission> = emptyList(),
    val next: AttendanceMission? = null,
    val now: Instant,
    /** The assignees of a mission about to start, or just started, with whether they are there. */
    val assignees: List<AttendanceVolunteer> = emptyList(),
    /** How many volunteers that mission is short of, 0 when it has them all. */
    val short: Int = 0,
    /** Whether the check-ins of the mission the [assignees] belong to have arrived. */
    val attendanceKnown: Boolean = false,
    /** A line under the headline: a mission's hours and place, or how many are assigned. */
    val detail: String? = null,
)

/** One volunteer of the card, and whether they were just checked in by hand. */
private data class TeamNowRow(val assignee: MissingAssignee, val justCheckedIn: Boolean)

/**
 * A row the manager has tapped, held until a moment after the last tap on it. [taps] counts the
 * taps still waiting out their moment, so a row tapped again is not taken away by the timer of the
 * tap before it.
 */
private class PendingCheckIn(val assignee: MissingAssignee) {
  var taps = 1
}

/** What tells one row from another: a volunteer has a row per mission they are missing from. */
private data class TeamNowRowId(val missionId: String, val userId: String)

private val MissingAssignee.rowId
  get() = TeamNowRowId(mission.id, volunteer.userId)

/**
 * A volunteer of the card: the row of the mission list in the card's own clothes, tappable until
 * they are checked in.
 */
@Composable
private fun VolunteerRow(row: TeamNowRow, now: Instant, onCheckIn: (MissingAssignee) -> Unit) {
  val assignee = row.assignee
  val missionId = assignee.mission.id
  val userId = assignee.volunteer.userId
  val checkedIn = row.justCheckedIn
  Box(
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(
                  if (checkedIn)
                      lerp(MaterialTheme.colorScheme.surfaceVariant, shifter_success, GREEN_TINT)
                  else MaterialTheme.colorScheme.surfaceVariant
              )
              .testTag(
                  if (checkedIn) TeamNowCardTestTags.justCheckedIn(missionId, userId)
                  else TeamNowCardTestTags.missing(missionId, userId)
              )
  ) {
    AttendanceVolunteerRow(
        volunteer = assignee.volunteer,
        checkInTestTag = TeamNowCardTestTags.checkIn(missionId, userId),
        leading = { Avatar(assignee.volunteer) },
        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        gap = 12.dp,
        nameStyle = NAME,
        subtitle = {
          if (checkedIn) {
            Detail(stringResource(R.string.attendance_now_checked_in), shifter_success)
          } else {
            Detail(
                stringResource(
                    R.string.attendance_now_late,
                    assignee.mission.title,
                    formatDuration(Duration.between(assignee.mission.startAt, now)),
                ),
                shifter_warning,
            )
          }
        },
        onCheckIn = if (checkedIn) null else ({ onCheckIn(assignee) }),
    ) {
      if (checkedIn) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = shifter_success,
            modifier = Modifier.size(22.dp),
        )
      } else {
        // The words of the tap, whose target is the whole row rather than them alone.
        Text(
            text = stringResource(R.string.attendance_now_check_in),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(12.dp),
        )
      }
    }
  }
}

/**
 * The volunteer's initial, for a team the manager knows by face rather than by id: the shared
 * [avatarInitials], of one letter as the design draws it, and "?" for a profile that could not be
 * read.
 */
@Composable
private fun Avatar(volunteer: AttendanceVolunteer) {
  Box(
      // Decorative: the initial repeats the first letter of the name beside it, so it is drawn
      // and not spoken, rather than read out as "C, Chloe Dubois".
      modifier =
          Modifier.size(36.dp)
              .clip(CircleShape)
              .background(shifter_avatarBackground)
              .clearAndSetSemantics {},
      contentAlignment = Alignment.Center,
  ) {
    Text(
        text = avatarInitials(volunteer.displayName, maxInitials = 1),
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
    )
  }
}

/** [text] followed by where the mission is, when it has an address: "4 assigned · Hall B". */
@Composable
private fun AttendanceMission.andPlace(text: String): String =
    address?.let { stringResource(R.string.attendance_mission_subtitle, text, it) } ?: text

/**
 * An assignee of the mission at hand: there already, or still expected. Neither is said while the
 * mission's attendance is not [known][AttendanceMission.attendanceKnown], as on the first load or
 * when the check-ins could not be observed: nobody is shown present then, so "Expected" would
 * report an absence nobody has seen.
 */
@Composable
private fun AssigneeRow(volunteer: AttendanceVolunteer, attendanceKnown: Boolean) {
  val arrived = volunteer.checkedInAt != null
  AttendanceVolunteerRow(
      volunteer = volunteer,
      // Unused here: checking a volunteer in by hand belongs to the missing ones.
      checkInTestTag = "",
      leading = { Avatar(volunteer) },
      gap = 12.dp,
      nameStyle = NAME,
  ) {
    if (attendanceKnown) {
      Detail(
          stringResource(
              if (arrived) R.string.attendance_now_volunteer_arrived
              else R.string.attendance_now_volunteer_expected
          ),
          if (arrived) shifter_success else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** A line of the card under the rows, such as how many others are at work. */
@Composable
private fun Line(text: String) {
  Text(
      text = text,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontSize = 14.sp,
      lineHeight = 18.sp,
  )
}

/** The second line of a row: how late they are, or that they are now checked in. */
@Composable
private fun Detail(text: String, color: Color) {
  Text(text = text, color = color, fontSize = 12.sp, lineHeight = 16.sp)
}

// A style built from scratch has no typeface of its own, so it names the theme's one.
private val NAME =
    TextStyle(
        fontFamily = SatoshiFontFamily,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    )

/** How much of the success green a row takes once its volunteer is checked in. */
private const val GREEN_TINT = 0.25f
