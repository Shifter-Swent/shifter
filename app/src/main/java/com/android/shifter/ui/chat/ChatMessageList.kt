// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.model.time.formatDay
import com.swent.shifter.ui.theme.shifter_mutedText
import java.time.LocalDate
import java.time.ZoneId

/** A row of the list of messages. */
internal sealed interface ChatRow {
  /** The key of the row in the list, a message's id for a message. */
  val key: String

  /**
   * The chip of [day], above its first message. It is keyed on the day, which has one chip only, so
   * the chip stays in place when its first message drops out of the channel or is confirmed.
   */
  data class Day(val day: LocalDate) : ChatRow {
    // Not a message id: a Firestore id has no colon.
    override val key = "day:$day"
  }

  /**
   * The [message], the [first][startsGroup] and the [last][endsGroup] of a run of messages from the
   * same sender: the first shows the name, the last the avatar, the time and the bubble's tail.
   */
  data class Message(
      val message: ChatMessageItem,
      val startsGroup: Boolean,
      val endsGroup: Boolean,
  ) : ChatRow {
    override val key = message.id
  }
}

/**
 * The rows of [messages]: a chip before the first message of each day in [zone], and each message
 * placed in its run of messages from the same sender that same day. A message still being sent
 * belongs to [today]. An announcement stands alone, so it is never read as part of a conversation.
 *
 * A message is never placed on a day before the one of the message above it: one dated by the
 * device clock, behind the server's after midnight, would otherwise bring an earlier day back below
 * a later one, and that day would get a second chip.
 */
internal fun chatRows(
    messages: List<ChatMessageItem>,
    zone: ZoneId,
    today: LocalDate,
): List<ChatRow> {
  val days =
      messages
          .runningFold(LocalDate.MIN) { previous, message ->
            maxOf(previous, message.sentAt?.atZone(zone)?.toLocalDate() ?: today)
          }
          .drop(1)

  fun sameGroup(a: Int, b: Int): Boolean {
    val first = messages.getOrNull(a) ?: return false
    val second = messages.getOrNull(b) ?: return false
    return !first.isAnnouncement &&
        !second.isAnnouncement &&
        first.isMine == second.isMine &&
        first.senderId == second.senderId &&
        days[a] == days[b]
  }

  val rows = mutableListOf<ChatRow>()
  messages.forEachIndexed { index, message ->
    if (index == 0 || days[index - 1] != days[index]) rows += ChatRow.Day(days[index])
    rows +=
        ChatRow.Message(
            message,
            startsGroup = !sameGroup(index - 1, index),
            endsGroup = !sameGroup(index, index + 1),
        )
  }
  return rows
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChatMessageList(messages: List<ChatMessageItem>, zone: ZoneId, today: LocalDate) {
  val rows = remember(messages, zone, today) { chatRows(messages, zone, today) }
  val listState = rememberLazyListState()
  val latest = messages.last()
  // Keyed on the latest message rather than the count, which stays at the cap of the channel. It is
  // followed only from the bottom, or when it is the user's own, so reading older ones is not
  // interrupted; the latest one already followed tells whether the list was at the bottom.
  var followedId by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(latest.id) {
    val atBottom =
        followedId == null || listState.layoutInfo.visibleItemsInfo.any { it.key == followedId }
    if (atBottom || latest.isMine) listState.scrollToItem(rows.lastIndex)
    followedId = latest.id
  }
  // Whether it is open, not its height, so the animation of the keyboard does not restart this.
  val keyboardOpen = WindowInsets.isImeVisible
  LaunchedEffect(keyboardOpen) { if (keyboardOpen) listState.scrollToItem(rows.lastIndex) }
  LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize().testTag(TeamChatTestTags.MESSAGES),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
  ) {
    rows.forEach { row ->
      when (row) {
        // Sticky, so the day of the messages on screen is always shown: the list opens at the
        // bottom, where an old message would otherwise show only its time and read as a current
        // one.
        is ChatRow.Day -> stickyHeader(key = row.key) { DayChip(row.day, today) }
        is ChatRow.Message -> item(key = row.key) { MessageRow(row, zone) }
      }
    }
  }
}

/** The centred chip of [day] above its first message: "Today", "Yesterday", or the date. */
@Composable
private fun DayChip(day: LocalDate, today: LocalDate) {
  val label =
      when (day) {
        today -> stringResource(R.string.chat_today)
        today.minusDays(1) -> stringResource(R.string.chat_yesterday)
        else -> day.formatDay(withYear = day.year != today.year)
      }
  Box(
      modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
      contentAlignment = Alignment.Center,
  ) {
    Text(
        label,
        color = shifter_mutedText,
        style = MaterialTheme.typography.labelSmall,
        modifier =
            Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .padding(horizontal = 10.dp, vertical = 3.dp)
                // A heading, so TalkBack can jump from one day to the next.
                .semantics { heading() }
                .testTag(TeamChatTestTags.day(day)),
    )
  }
}
