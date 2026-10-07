// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.ui.events.EventBadge
import com.swent.shifter.ui.events.EventCardUi
import com.swent.shifter.ui.events.MyEventsTestTags
import com.swent.shifter.ui.theme.ShifterTheme
import com.swent.shifter.ui.theme.shifter_divider
import com.swent.shifter.ui.theme.shifter_mutedText
import com.swent.shifter.ui.theme.shifter_success
import com.swent.shifter.ui.theme.shifter_successContainer
import com.swent.shifter.ui.theme.shifter_warning
import com.swent.shifter.ui.theme.shifter_warningContainer

/** The action link at the bottom right of an event card, e.g. "Withdraw" or "Manage event". */
data class EventCardAction(val label: String, val color: Color, val onClick: () -> Unit)

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
      Text(text = card.footerLabel, color = shifter_mutedText, fontSize = 12.sp)
      if (action != null) {
        Text(
            text = action.label,
            color = action.color,
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
      text = "● ${badge.label}",
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

@Composable
private fun EventMeta(@DrawableRes icon: Int, text: String, modifier: Modifier = Modifier) {
  Row(
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier = modifier,
  ) {
    Image(painter = painterResource(icon), contentDescription = null, Modifier.size(14.dp))
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
  }
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
                footerLabel = "Role: Volunteer",
            ),
        action = EventCardAction("Withdraw", MaterialTheme.colorScheme.error) {},
    )
  }
}
