// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.ui.events.EventBadge
import com.swent.shifter.ui.events.EventCardFooter
import com.swent.shifter.ui.events.EventCardUi
import com.swent.shifter.ui.events.MyEventsTestTags
import com.swent.shifter.ui.theme.ShifterTheme
import com.swent.shifter.ui.theme.shifter_divider
import com.swent.shifter.ui.theme.shifter_mutedText
import com.swent.shifter.ui.theme.shifter_success
import com.swent.shifter.ui.theme.shifter_successContainer
import com.swent.shifter.ui.theme.shifter_warning
import com.swent.shifter.ui.theme.shifter_warningContainer

/** The kinds of action link an event card can show at the bottom right. */
enum class EventCardActionType {
  WITHDRAW,
  MANAGE_EVENT,
}

/** The action link of an event card. Its label and color follow from [type]. */
data class EventCardAction(val type: EventCardActionType, val onClick: () -> Unit)

/** One event of a My Events list. The [action] link is hidden when it is null. */
@Composable
fun EventCard(card: EventCardUi, action: EventCardAction?, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(16.dp)
  Column(
      verticalArrangement = Arrangement.spacedBy(10.dp),
      modifier =
          modifier
              .fillMaxWidth()
              .clip(shape)
              .background(colors.surfaceVariant)
              .border(1.dp, colors.outlineVariant, shape)
              .padding(16.dp)
              .testTag(MyEventsTestTags.eventCard(card.id)),
  ) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
      StatusPill(card.badge, Modifier.testTag(MyEventsTestTags.statusPill(card.id)))
      Text(text = card.dateLabel, color = colors.onSurfaceVariant, fontSize = 12.sp)
    }

    Text(
        text = card.title,
        color = colors.onSurface,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      EventMeta(R.drawable.ic_map_pin, card.locationLabel, Modifier.weight(1f, fill = false))
      EventMeta(R.drawable.ic_clock, card.timeLabel)
    }

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
          text = stringResource(card.footer.labelRes),
          color = shifter_mutedText,
          fontSize = 12.sp,
      )
      if (action != null) {
        Text(
            text = stringResource(action.type.labelRes),
            color =
                when (action.type) {
                  EventCardActionType.WITHDRAW -> colors.error
                  EventCardActionType.MANAGE_EVENT -> colors.primary
                },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier =
                Modifier.clickable(onClick = action.onClick)
                    .testTag(MyEventsTestTags.eventAction(card.id)),
        )
      }
    }
  }
}

/** Colored "● Label" pill, colored after the [badge]. */
@Composable
fun StatusPill(badge: EventBadge, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  val (content, container) =
      when (badge) {
        EventBadge.CONFIRMED,
        EventBadge.ONGOING -> shifter_success to shifter_successContainer
        EventBadge.PENDING_APPROVAL -> shifter_warning to shifter_warningContainer
        EventBadge.IN_PREPARATION -> colors.primary to colors.primaryContainer
        EventBadge.ENDED -> colors.onSurfaceVariant to shifter_divider
      }
  Text(
      text = stringResource(R.string.my_events_status_pill, stringResource(badge.labelRes)),
      color = content,
      fontSize = 11.sp,
      fontWeight = FontWeight.SemiBold,
      modifier =
          modifier
              .clip(RoundedCornerShape(20.dp))
              .background(container)
              .padding(horizontal = 10.dp, vertical = 4.dp),
  )
}

/** An icon and its text. The icon is tinted like the text, so both follow the theme. */
@Composable
private fun EventMeta(@DrawableRes icon: Int, text: String, modifier: Modifier = Modifier) {
  val color = MaterialTheme.colorScheme.onSurfaceVariant
  Row(
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier = modifier,
  ) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(14.dp),
    )
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
  }
}

@get:StringRes
private val EventBadge.labelRes: Int
  get() =
      when (this) {
        EventBadge.CONFIRMED -> R.string.my_events_badge_confirmed
        EventBadge.PENDING_APPROVAL -> R.string.my_events_badge_pending_approval
        EventBadge.IN_PREPARATION -> R.string.my_events_badge_in_preparation
        EventBadge.ONGOING -> R.string.my_events_badge_ongoing
        EventBadge.ENDED -> R.string.my_events_badge_ended
      }

@get:StringRes
private val EventCardFooter.labelRes: Int
  get() =
      when (this) {
        EventCardFooter.VOLUNTEER -> R.string.my_events_footer_volunteer
        EventCardFooter.ORGANIZER -> R.string.my_events_footer_organizer
        EventCardFooter.AWAITING_APPROVAL -> R.string.my_events_footer_awaiting_approval
      }

@get:StringRes
private val EventCardActionType.labelRes: Int
  get() =
      when (this) {
        EventCardActionType.WITHDRAW -> R.string.my_events_action_withdraw
        EventCardActionType.MANAGE_EVENT -> R.string.my_events_action_manage_event
      }

@Preview
@Composable
private fun EventCardPreview() {
  ShifterTheme {
    EventCard(
        card =
            EventCardUi(
                id = "city-marathon",
                title = "City Marathon 2025",
                dateLabel = "Sat 14 Jun 2025",
                locationLabel = "Lyon, Place Bellecour",
                timeLabel = "08:00 – 14:00",
                badge = EventBadge.CONFIRMED,
                footer = EventCardFooter.VOLUNTEER,
            ),
        action = EventCardAction(EventCardActionType.WITHDRAW) {},
    )
  }
}
