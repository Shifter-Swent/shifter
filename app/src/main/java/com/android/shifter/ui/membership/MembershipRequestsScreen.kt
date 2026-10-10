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
import com.swent.shifter.ui.theme.ShifterTheme
import com.swent.shifter.ui.theme.shifter_success
import com.swent.shifter.ui.theme.shifter_successContainer
import com.swent.shifter.ui.theme.shifter_warning
import com.swent.shifter.ui.theme.shifter_warningContainer
import java.time.Instant

@Composable
fun MembershipRequestsScreen(
    uiState: MembershipRequestUIState,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  LazyColumn(
      modifier = modifier.fillMaxSize().background(colors.background),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (uiState.isLoading) {
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
          enabled = !uiState.isLoading,
      )
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
    enabled: Boolean = true,
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
          modifier = Modifier.weight(1f),
      )
      RequestStatusPill(item.request.status)
    }
    if (item.request.status == MembershipRequestStatus.PENDING) {
      val buttonShape = RoundedCornerShape(14.dp)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onAccept,
            enabled = enabled && !isProcessing,
            shape = buttonShape,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            modifier = Modifier.weight(1f).height(40.dp),
        ) {
          ActionLabel(R.string.membership_requests_accept)
        }
        OutlinedButton(
            onClick = onReject,
            enabled = enabled && !isProcessing,
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
private fun RequestStatusPill(status: MembershipRequestStatus) {
  val colors = MaterialTheme.colorScheme
  val (text, content, container) =
      when (status) {
        MembershipRequestStatus.PENDING ->
            Triple(
                R.string.membership_requests_status_pending,
                shifter_warning,
                shifter_warningContainer,
            )
        MembershipRequestStatus.ACCEPTED ->
            Triple(
                R.string.membership_requests_status_accepted,
                shifter_success,
                shifter_successContainer,
            )
        MembershipRequestStatus.REJECTED ->
            Triple(
                R.string.membership_requests_status_rejected,
                colors.error,
                colors.errorContainer,
            )
      }
  Text(
      text = stringResource(text),
      color = content,
      fontSize = 11.sp,
      fontWeight = FontWeight.SemiBold,
      modifier =
          Modifier.clip(RoundedCornerShape(20.dp))
              .background(container)
              .padding(horizontal = 10.dp, vertical = 4.dp),
  )
}

@Preview
@Composable
private fun MembershipRequestsScreenPreview() {
  val request =
      MembershipRequest(
          id = "alice",
          userId = "alice",
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
