// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.model.team.Team
import com.swent.shifter.ui.components.Pill
import com.swent.shifter.ui.components.PillColors
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant

object ParticipantsListTestTags {
  const val LOADING = "ParticipantsLoading"
  const val ERROR = "ParticipantsError"
  const val RETRY_BUTTON = "ParticipantsRetryButton"
  const val EMPTY = "ParticipantsEmpty"

  /** The bar above a team's participants, or above those of no team when [teamId] is null. */
  fun teamBar(teamId: String?) = "ParticipantsTeamBar_${teamId ?: NO_TEAM}"

  /** The line of a team nobody has joined yet. */
  fun noParticipants(teamId: String) = "ParticipantsNoParticipants_$teamId"

  fun participantCard(userId: String) = "ParticipantCard_$userId"

  fun managerBadge(userId: String) = "ParticipantManagerBadge_$userId"

  private const val NO_TEAM = "none"
}

/**
 * The participants of the event, one card each, under a bar naming their team. Teams nobody has
 * joined yet are listed too, and the participants of no team come last.
 *
 * @param onParticipantClick called with a participant and their team when their card is tapped.
 *   Cards of no team cannot be tapped, since there is no team to act on.
 */
@Composable
fun ParticipantsList(
    state: ParticipantsUiState,
    onRetry: () -> Unit,
    onParticipantClick: (ParticipantItem, Team) -> Unit,
    modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  LazyColumn(
      modifier = modifier.fillMaxSize().background(colors.background),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (state.isLoading) {
      item {
        CircularProgressIndicator(
            color = colors.primary,
            modifier = Modifier.testTag(ParticipantsListTestTags.LOADING),
        )
      }
    }
    state.error?.let { error ->
      item { ErrorMessage(error, enabled = !state.isLoading, onRetry = onRetry) }
    }
    if (!state.isLoading && state.error == null && state.groups.isEmpty()) {
      item {
        Text(
            text = stringResource(R.string.participants_empty),
            color = colors.onSurfaceVariant,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp).testTag(ParticipantsListTestTags.EMPTY),
        )
      }
    }
    state.groups.forEach { group ->
      item(key = "team-${group.team?.id}") { TeamBar(group) }
      val team = group.team
      if (team != null && group.participants.isEmpty()) {
        item(key = "empty-${team.id}") {
          Text(
              text = stringResource(R.string.participants_team_empty),
              color = colors.onSurfaceVariant,
              fontSize = 14.sp,
              modifier =
                  Modifier.fillMaxWidth()
                      .padding(horizontal = 4.dp)
                      .testTag(ParticipantsListTestTags.noParticipants(team.id)),
          )
        }
      }
      items(group.participants, key = { "participant-${it.userId}" }) { participant ->
        ParticipantCard(
            participant = participant,
            onClick = team?.let { { onParticipantClick(participant, it) } },
        )
      }
    }
  }
}

/** The failure, in the words of the Requests tab, with a way to try again. */
@Composable
private fun ErrorMessage(error: ParticipantsError, enabled: Boolean, onRetry: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
        text = stringResource(error.message),
        color = colors.error,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag(ParticipantsListTestTags.ERROR),
    )
    TextButton(
        onClick = onRetry,
        enabled = enabled,
        modifier = Modifier.testTag(ParticipantsListTestTags.RETRY_BUTTON),
    ) {
      Text(text = stringResource(R.string.participants_retry), fontWeight = FontWeight.SemiBold)
    }
  }
}

/** A thin bar with the team's name and participant count set on it. */
@Composable
private fun TeamBar(group: ParticipantGroup) {
  val colors = MaterialTheme.colorScheme
  val name = group.team?.name ?: stringResource(R.string.participants_no_team)
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier =
          Modifier.fillMaxWidth()
              .padding(top = 4.dp)
              .testTag(ParticipantsListTestTags.teamBar(group.team?.id)),
  ) {
    HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.width(16.dp))
    Text(
        text = stringResource(R.string.participants_team_bar, name, group.participants.size),
        color = colors.onSurfaceVariant,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.weight(1f))
  }
}

/**
 * One participant, drawn like the cards of the Requests tab: their name, and a badge when they
 * manage their team.
 *
 * @param onClick null when the card cannot be tapped.
 */
@Composable
private fun ParticipantCard(participant: ParticipantItem, onClick: (() -> Unit)?) {
  val colors = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(16.dp)
  Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier =
          Modifier.fillMaxWidth()
              .clip(shape)
              .background(colors.surfaceVariant)
              .border(1.dp, colors.outlineVariant, shape)
              .then(
                  if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
                  else Modifier
              )
              .padding(16.dp)
              .testTag(ParticipantsListTestTags.participantCard(participant.userId)),
  ) {
    Text(
        text = participant.name ?: stringResource(R.string.participants_unknown_volunteer),
        color = colors.onSurface,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
    if (participant.isManager) {
      Pill(
          text = stringResource(R.string.participants_manager),
          colors = PillColors(colors.primary, colors.primaryContainer),
          modifier = Modifier.testTag(ParticipantsListTestTags.managerBadge(participant.userId)),
      )
    }
  }
}

private val ParticipantsError.message: Int
  get() =
      when (this) {
        ParticipantsError.OFFLINE -> R.string.participants_error_offline
        ParticipantsError.PERMISSION_DENIED -> R.string.participants_error_permission_denied
        ParticipantsError.UNEXPECTED -> R.string.participants_error_unexpected
      }

@Preview
@Composable
private fun ParticipantsListPreview() {
  fun team(id: String, name: String) =
      Team(
          id = id,
          eventId = "event",
          name = name,
          description = "",
          icon = "",
          createdAt = Instant.EPOCH,
      )
  ShifterTheme {
    ParticipantsList(
        state =
            ParticipantsUiState(
                groups =
                    listOf(
                        ParticipantGroup(
                            team("bar", "Bar"),
                            listOf(
                                ParticipantItem("alice", "Alice Martin", isManager = true),
                                ParticipantItem("bob", "Bob Rossi", isManager = false),
                            ),
                        ),
                        ParticipantGroup(
                            team("logistics", "Logistics"),
                            listOf(),
                        ),
                        ParticipantGroup(null, listOf(ParticipantItem("carol", null, false))),
                    )
            ),
        onRetry = {},
        onParticipantClick = { _, _ -> },
    )
  }
}
