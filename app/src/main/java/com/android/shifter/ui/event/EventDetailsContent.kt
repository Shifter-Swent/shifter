// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.model.event.Event
import com.swent.shifter.ui.theme.shifter_mutedText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object EventDetailsTestTags {
  const val BACK_BUTTON = "EventDetailsBackButton"
  const val CATEGORY = "EventDetailsCategory"
  const val DATE = "EventDetailsDate"
  const val VENUE = "EventDetailsVenue"
  const val DESCRIPTION = "EventDetailsDescription"
}

/**
 * The "Event details · Apply" step of the Figma: what the volunteer is applying to, with Apply kept
 * at the bottom of the screen.
 *
 * Places and "Organized by" are not shown yet: the event has no capacity field, and organizer
 * profiles cannot be read by other users.
 */
@Composable
fun EventDetailsContent(
    event: Event,
    uiState: JoinEventUiState,
    onBack: () -> Unit,
    onApply: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Column(modifier = Modifier.fillMaxSize()) {
    DetailsTopBar(onBack)
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier =
            Modifier.weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
      IconLabel(
          Icons.Outlined.QrCode2,
          stringResource(R.string.event_details_opened_from_code),
          colors.primary,
      )
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(event.type.label),
            color = colors.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag(EventDetailsTestTags.CATEGORY),
        )
        Text(
            text = event.title,
            color = colors.onBackground,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(JoinEventScreenTestTags.EVENT_TITLE),
        )
      }
      WhenAndWhereCard(event)
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.event_details_about),
            color = colors.onBackground,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = event.description,
            color = colors.onSurfaceVariant,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = Modifier.testTag(EventDetailsTestTags.DESCRIPTION),
        )
      }
      IconLabel(
          Icons.Outlined.Info,
          stringResource(R.string.event_details_approval_notice),
          colors.onSurfaceVariant,
      )
    }
    ApplyBar(uiState, onApply)
  }
}

@Composable
private fun DetailsTopBar(onBack: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    IconButton(
        onClick = onBack,
        modifier =
            Modifier.size(40.dp)
                .clip(CircleShape)
                .background(colors.surfaceVariant)
                .testTag(EventDetailsTestTags.BACK_BUTTON),
    ) {
      Icon(
          Icons.AutoMirrored.Filled.ArrowBack,
          stringResource(R.string.content_description_back),
          tint = colors.onSurface,
      )
    }
    Text(
        text = stringResource(R.string.event_details_title),
        color = colors.onBackground,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
    )
  }
}

/** Date & time and venue, in one card as in the Figma. */
@Composable
private fun WhenAndWhereCard(event: Event) {
  val colors = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(16.dp)
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .clip(shape)
              .background(colors.surfaceVariant)
              .border(1.dp, colors.outlineVariant, shape)
              .padding(horizontal = 16.dp)
  ) {
    InfoRow(
        Icons.Outlined.CalendarMonth,
        stringResource(R.string.event_details_date_time),
        formatEventDate(event.startAt),
        EventDetailsTestTags.DATE,
    )
    HorizontalDivider(color = colors.outlineVariant)
    InfoRow(
        Icons.Outlined.LocationOn,
        stringResource(R.string.event_details_venue),
        event.location.address,
        EventDetailsTestTags.VENUE,
    )
  }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, valueTestTag: String) {
  val colors = MaterialTheme.colorScheme
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      modifier = Modifier.padding(vertical = 16.dp),
  ) {
    Icon(icon, contentDescription = null, tint = colors.primary)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(text = label, color = colors.onSurfaceVariant, fontSize = 13.sp)
      Text(
          text = value,
          color = colors.onSurface,
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.testTag(valueTestTag),
      )
    }
  }
}

@Composable
private fun IconLabel(icon: ImageVector, text: String, color: Color) {
  Row(
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
    Text(text = text, color = color, fontSize = 14.sp, lineHeight = 20.sp)
  }
}

/** Apply pinned to the bottom, replaced by the request's status once it is sent. */
@Composable
private fun ApplyBar(uiState: JoinEventUiState, onApply: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  Column(modifier = Modifier.fillMaxWidth()) {
    HorizontalDivider(color = colors.outlineVariant)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
      uiState.error?.let { ErrorText(it) }
      val requestStatus = uiState.requestStatus
      Box(contentAlignment = Alignment.Center, modifier = Modifier.height(58.dp)) {
        when {
          uiState.isLoading -> CircularProgressIndicator(color = colors.primary)
          requestStatus != null -> StatusBadge(requestStatus)
          else ->
              PrimaryButton(
                  stringResource(R.string.join_event_apply),
                  onApply,
                  JoinEventScreenTestTags.APPLY_BUTTON,
              )
        }
      }
      Text(
          text = stringResource(R.string.event_details_apply_hint),
          color = shifter_mutedText,
          fontSize = 13.sp,
          textAlign = TextAlign.Center,
      )
    }
  }
}

private val EVENT_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.ENGLISH)

/** "Sat 22 Jun · 09:30", in the device's time zone unless [zone] says otherwise. */
internal fun formatEventDate(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    EVENT_DATE_FORMAT.format(instant.atZone(zone))
