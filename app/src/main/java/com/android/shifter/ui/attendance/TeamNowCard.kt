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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.ui.components.PrimaryButton
import com.swent.shifter.ui.components.avatarInitials
import com.swent.shifter.ui.components.nowcard.NowCardContainer
import com.swent.shifter.ui.components.nowcard.NowCardTestTags
import com.swent.shifter.ui.components.nowcard.NowTone
import com.swent.shifter.ui.components.nowcard.TimeBar
import com.swent.shifter.ui.components.nowcard.accent
import com.swent.shifter.ui.components.time.formatDuration
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
 * The manager's now card while people are missing: who is not there, how late they are, and the one
 * tap that checks them in.
 *
 * The card draws the state it is given and reports what the manager does. It holds no attendance of
 * its own, apart from who was just checked in, whom the state stops reporting as soon as the write
 * is pending. Missions whose attendance is not known are said to be unknown rather than counted as
 * nobody there.
 *
 * @param now the clock's tick, which the lateness and the time bars are read at.
 * @param onCheckIn checks a volunteer in by hand, on the mission their row belongs to.
 * @param onViewOnMap shows the team on the map, the card's only action.
 */
@Composable
fun TeamNowCard(
    state: TeamNowState.Missing,
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
  val justCheckedIn = remember { mutableStateListOf<MissingAssignee>() }
  val scope = rememberCoroutineScope()
  val checkIn: (MissingAssignee) -> Unit = { assignee ->
    justCheckedIn += assignee
    onCheckIn(assignee.mission.id, assignee.volunteer.userId)
    // A row in green is not tappable, so no second tap can add this row again before it goes,
    // and this timer can only remove what this tap put there.
    scope.launch {
      delay(JUST_CHECKED_IN_MILLIS.milliseconds)
      justCheckedIn.removeAll { it.rowId == assignee.rowId }
    }
  }

  val green = justCheckedIn.mapTo(mutableSetOf()) { it.rowId }
  val reported = state.missing.mapTo(mutableSetOf()) { it.rowId }
  // Rows the state no longer reports are kept where the eye can still find them: after the rest.
  val left = justCheckedIn.filter { it.rowId !in reported }
  val rows =
      state.missing.map { TeamNowRow(it, it.rowId in green) } +
          left.map { TeamNowRow(it, justCheckedIn = true) }
  // "N volunteers missing" counts people, not rows: one volunteer missing from two missions is
  // one person to find. It counts what the state reports, so a volunteer just checked in is shown
  // in green without the count reading zero before the state has caught up.
  val count = state.missing.distinctBy { it.volunteer.userId }.size
  // onDuty leaves out everyone the state still reports missing, so only a volunteer it has
  // dropped from every mission is one of the others, and each of them once however many rows
  // they are shown in.
  val stillMissing = state.missing.mapTo(mutableSetOf()) { it.volunteer.userId }
  val shownPresent = justCheckedIn.mapTo(mutableSetOf()) { it.volunteer.userId } - stillMissing

  NowCardContainer(
      tone = NowTone.ALERT,
      status = stringResource(R.string.attendance_now_needs_you),
      headline = pluralStringResource(R.plurals.attendance_now_missing, count, count),
      state =
          TeamNowCardBody(
              rows = rows,
              missions = state.missing.map { it.mission }.distinctBy { it.id },
              // A volunteer shown in green is a row of their own, so not one of the others.
              others = (state.onDuty - shownPresent.size).coerceAtLeast(0),
              attendanceUnknown = state.attendanceUnknown,
              now = now,
          ),
      modifier = modifier,
  ) { body ->
    body.missions.forEach { mission ->
      TimeBar(
          from = mission.startAt,
          to = mission.endAt,
          now = body.now,
          // The bar says the mission is running; the card's tone says its team is not all there.
          color = NowTone.LIVE.accent,
          zone = zone,
          title = mission.title,
      )
    }
    body.rows.forEach { row -> VolunteerRow(row, body.now, checkIn) }
    if (body.others > 0) {
      Line(pluralStringResource(R.plurals.attendance_now_others, body.others, body.others))
    }
    if (body.attendanceUnknown.isNotEmpty()) {
      val unknown = body.attendanceUnknown.size
      Line(pluralStringResource(R.plurals.attendance_now_unknown, unknown, unknown))
    }
    PrimaryButton(
        text = stringResource(R.string.attendance_now_view_on_map),
        onClick = onViewOnMap,
        testTag = TeamNowCardTestTags.ACTION,
        icon = Icons.Filled.Map,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = NowTone.ALERT.accent,
                contentColor = MaterialTheme.colorScheme.surface,
            ),
    )
  }
}

/** What the card draws, kept whole by the shell while a state slides away. */
private data class TeamNowCardBody(
    val rows: List<TeamNowRow>,
    val missions: List<AttendanceMission>,
    val others: Int,
    val attendanceUnknown: List<AttendanceMission>,
    val now: Instant,
)

/** One volunteer of the card, and whether they were just checked in by hand. */
private data class TeamNowRow(val assignee: MissingAssignee, val justCheckedIn: Boolean)

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
      modifier = Modifier.size(36.dp).clip(CircleShape).background(shifter_avatarBackground),
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

private val NAME = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)

/** How much of the success green a row takes once its volunteer is checked in. */
private const val GREEN_TINT = 0.25f
