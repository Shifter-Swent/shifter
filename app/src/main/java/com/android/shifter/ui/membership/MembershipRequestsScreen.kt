// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.membership

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.ui.components.Pill
import com.swent.shifter.ui.components.RefreshableList
import com.swent.shifter.ui.components.membershipStatusColors
import com.swent.shifter.ui.components.refreshAction
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant

/** Tags of the requests list, per request: the volunteer's id identifies it within an event. */
object MembershipRequestsTestTags {
  fun status(userId: String) = "MembershipRequestStatus_$userId"

  fun acceptButton(userId: String) = "MembershipRequestAccept_$userId"
}

/**
 * The requests to join the event, one card each. Pulling the list down reloads it with [onRetry].
 */
@Composable
fun MembershipRequestsScreen(
    uiState: MembershipRequestUIState,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  RefreshableList(
      isLoading = uiState.isLoading,
      hasContent = uiState.requests.isNotEmpty(),
      onRefresh = onRetry,
      modifier = modifier.fillMaxSize(),
  ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // A reload keeps the list already shown instead of replacing it with the spinner.
      if (uiState.isLoading && uiState.requests.isEmpty()) {
        item { CircularProgressIndicator(color = colors.primary) }
      }
      uiState.errorMsg?.let { message ->
        item {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                color = colors.error,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            TextButton(
                onClick = onRetry,
                enabled = !uiState.isLoading && uiState.processingRequestIds.isEmpty(),
            ) {
              Text(
                  text = stringResource(R.string.membership_requests_retry),
                  fontWeight = FontWeight.SemiBold,
              )
            }
          }
        }
      }
      if (!uiState.isLoading && uiState.errorMsg == null && uiState.requests.isEmpty()) {
        item {
          Text(
              text = stringResource(R.string.membership_requests_empty),
              color = colors.onSurfaceVariant,
              fontSize = 14.sp,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(24.dp),
          )
        }
      }
      items(uiState.requests, key = { it.request.userId }) { item ->
        MembershipRequestCard(
            item = item,
            isProcessing = item.request.id in uiState.processingRequestIds,
            onAccept = { onAccept(item.request.userId) },
            onReject = { onReject(item.request.userId) },
            canAccept = !uiState.hasEnded,
        )
      }
    }
  }
}

/** One request, drawn like the cards of My Events. */
@Composable
fun MembershipRequestCard(
    item: MembershipRequestItemUIState,
    isProcessing: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
    canAccept: Boolean,
) {
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
              .padding(16.dp),
  ) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
          text = item.displayName,
          color = colors.onSurface,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          // TalkBack focuses the name: it offers the list's Refresh there.
          modifier = Modifier.weight(1f).refreshAction(),
      )
      RequestStatusPill(
          item.request.status,
          Modifier.testTag(MembershipRequestsTestTags.status(item.request.userId)),
      )
    }
    if (item.request.status == MembershipRequestStatus.PENDING) {
      val buttonShape = RoundedCornerShape(14.dp)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Once the event has ended, a request can only be rejected (#409).
        if (canAccept) {
          Button(
              onClick = onAccept,
              enabled = !isProcessing,
              shape = buttonShape,
              colors =
                  ButtonDefaults.buttonColors(
                      containerColor = colors.primary,
                      contentColor = colors.onPrimary,
                  ),
              modifier =
                  Modifier.weight(1f)
                      .height(40.dp)
                      .testTag(MembershipRequestsTestTags.acceptButton(item.request.userId)),
          ) {
            ActionLabel(R.string.membership_requests_accept)
          }
        }
        OutlinedButton(
            onClick = onReject,
            enabled = !isProcessing,
            shape = buttonShape,
            border = BorderStroke(1.dp, colors.outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
            modifier = Modifier.weight(1f).height(40.dp),
        ) {
          ActionLabel(R.string.membership_requests_reject)
        }
      }
    }
  }
}

@Composable
private fun ActionLabel(@StringRes text: Int) {
  Text(text = stringResource(text), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

/** Where the request stands, in the colors of the matching status of My Events. */
@Composable
private fun RequestStatusPill(status: MembershipRequestStatus, modifier: Modifier = Modifier) {
  val text =
      when (status) {
        MembershipRequestStatus.PENDING -> R.string.membership_requests_status_pending
        MembershipRequestStatus.ACCEPTED -> R.string.membership_requests_status_accepted
        MembershipRequestStatus.REJECTED -> R.string.membership_requests_status_rejected
      }
  Pill(text = stringResource(text), colors = membershipStatusColors(status), modifier = modifier)
}

@Preview
@Composable
private fun MembershipRequestsScreenPreview() {
  val request =
      MembershipRequest(
          id = "alice",
          userId = "alice",
          teamId = "bar",
          availability = listOf(AvailabilitySlot(Instant.EPOCH, Instant.EPOCH.plusSeconds(3600))),
          createdAt = Instant.EPOCH,
      )
  ShifterTheme {
    MembershipRequestsScreen(
        MembershipRequestUIState(
            requests = listOf(MembershipRequestItemUIState(request, "Alice Martin")),
            isLoading = false,
        ),
        {},
        {},
        {},
    )
  }
}
