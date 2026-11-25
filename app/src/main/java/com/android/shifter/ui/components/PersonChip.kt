// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.ui.theme.shifter_avatarBackground

/** Whether a [PersonChip] shows someone proposed for a mission, or already assigned to it. */
enum class PersonChipState {
  PROPOSED,
  ASSIGNED,
}

/**
 * A person on a mission: their initials in a disc, then their name. A proposed person is drawn in
 * the primary blue, someone already assigned in grey; screen readers say which, after the name, and
 * skip the initials, which only repeat it.
 *
 * @param name the person's name, never blank, or null when it could not be read: the chip then says
 *   so.
 * @param maxInitials how many initials the disc shows, e.g. 1 for a word rather than a name.
 */
@Composable
fun PersonChip(
    name: String?,
    state: PersonChipState,
    modifier: Modifier = Modifier,
    maxInitials: Int = 2,
) {
  val proposed = state == PersonChipState.PROPOSED
  val colors = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(50)
  val stateText =
      stringResource(if (proposed) R.string.person_chip_proposed else R.string.person_chip_assigned)
  Row(
      modifier =
          modifier
              .semantics(mergeDescendants = true) { stateDescription = stateText }
              .clip(shape)
              .background(if (proposed) colors.primaryContainer else colors.surfaceVariant)
              .border(1.dp, if (proposed) colors.primary else colors.outline, shape)
              .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
        modifier =
            Modifier.size(24.dp)
                .clip(CircleShape)
                .background(if (proposed) colors.primary else shifter_avatarBackground)
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
      Text(
          text = avatarInitials(name, maxInitials),
          fontSize = 10.sp,
          fontWeight = FontWeight.SemiBold,
          color = if (proposed) colors.onPrimary else colors.onSurfaceVariant,
      )
    }
    Text(
        text = name ?: stringResource(R.string.person_chip_unknown),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = if (proposed) colors.onSurface else colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 8.dp),
    )
  }
}
