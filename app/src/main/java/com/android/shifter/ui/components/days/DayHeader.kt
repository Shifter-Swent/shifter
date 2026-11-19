// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.days

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

object DayHeaderTestTags {
  const val HEADER = "dayHeader"
  const val TITLE = "dayHeaderTitle"
  const val BACK_TO_TODAY = "dayHeaderBackToToday"
}

/**
 * The top of a list of missions shown one day at a time, the same on every screen: the [title],
 * something on its right, then the [strip] of days.
 *
 * On the right of the title, in this order: the [filter] when the screen has one; else "Back to
 * today" while another day is shown; else the [hint]. A filter takes the room of "Back to today":
 * the strip's "Today" still brings today back.
 *
 * @param titleModifier for the title alone, e.g. an accessibility action that needs a node screen
 *   readers stop on.
 * @param filter e.g. the team filter; none when null.
 * @param onBackToToday shows today again, offered while another day is shown; none when null.
 * @param hint e.g. a subtitle or a count, when nothing else takes its place; none when null.
 * @param strip the days to pick from, or e.g. none for an event of one day.
 */
@Composable
fun DayHeader(
    title: String,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    filter: (@Composable () -> Unit)? = null,
    onBackToToday: (() -> Unit)? = null,
    hint: (@Composable () -> Unit)? = null,
    strip: (@Composable () -> Unit)? = null,
) {
  Column(
      modifier = modifier.fillMaxWidth().testTag(DayHeaderTestTags.HEADER),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        // As tall with a filter as without, so the strip does not move when one shows.
        modifier = Modifier.heightIn(min = 32.dp),
    ) {
      Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onBackground,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = titleModifier.weight(1f).testTag(DayHeaderTestTags.TITLE),
      )
      when {
        filter != null -> filter()
        onBackToToday != null ->
            BackToToday(onBackToToday, Modifier.testTag(DayHeaderTestTags.BACK_TO_TODAY))
        hint != null -> hint()
      }
    }
    strip?.invoke()
  }
}
