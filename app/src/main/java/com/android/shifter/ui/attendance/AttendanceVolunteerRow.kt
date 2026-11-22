// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter.ui.attendance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.model.time.formatTime
import com.swent.shifter.ui.theme.shifter_mutedText
import java.time.ZoneId

/**
 * Displays a volunteer's attendance information with an optional manual check-in action.
 *
 * The caller provides the section styling, test tags, and a mission-bound check-in callback.
 */
@Composable
internal fun AttendanceVolunteerRow(
    volunteer: AttendanceVolunteer,
    checkInTestTag: String,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    gap: Dp = 8.dp,
    nameStyle: TextStyle = MaterialTheme.typography.bodySmall,
    subtitle: @Composable () -> Unit = {},
    onCheckIn: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
  val name = attendanceVolunteerName(volunteer)
  Row(
      modifier =
          modifier
              .fillMaxWidth()
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
                            onCheckIn()
                          }
                          .testTag(checkInTestTag)
              ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(gap),
  ) {
    leading()
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(text = name, style = nameStyle, color = MaterialTheme.colorScheme.onSurface)
      subtitle()
    }
    trailing()
  }
}

/**
 * The name of [volunteer], or a stand-in when their profile could not be read: an id is not a name,
 * so someone unnamed reads the same wherever they are listed.
 */
@Composable
private fun attendanceVolunteerName(volunteer: AttendanceVolunteer): String =
    volunteer.displayName?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.attendance_volunteer_unknown)

/**
 * What is known of a volunteer's check-in on a mission card: checked in by hand, not confirmed by
 * the backend yet, and when they arrived. Emitted into the row it trails.
 */
@Composable
internal fun AttendanceCheckInDetails(
    volunteer: AttendanceVolunteer,
    zone: ZoneId,
    manualTestTag: String,
    pendingTestTag: String,
) {
  if (volunteer.checkedInManually) {
    Text(
        text = stringResource(R.string.attendance_manual),
        style = MaterialTheme.typography.labelSmall,
        color = shifter_mutedText,
        modifier = Modifier.testTag(manualTestTag),
    )
  }
  if (volunteer.checkInPending) {
    // Not confirmed yet: in its undo window, or waiting for the network.
    Icon(
        Icons.Filled.CloudOff,
        contentDescription = stringResource(R.string.attendance_not_synced),
        tint = shifter_mutedText,
        modifier = Modifier.size(14.dp).testTag(pendingTestTag),
    )
  }
  volunteer.checkedInAt?.let {
    Text(
        text = it.formatTime(zone),
        style = MaterialTheme.typography.labelSmall,
        color = shifter_mutedText,
    )
  }
}
