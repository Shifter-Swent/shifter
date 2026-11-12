// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.people

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import com.swent.shifter.ui.components.avatarInitials
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

  /** The action of an open card that moves the participant to another team, or into one. */
  fun moveButton(userId: String) = "ParticipantMoveButton_$userId"

  /** The action of an open card that makes the participant their team's manager, or stops it. */
  fun managerButton(userId: String) = "ParticipantManagerButton_$userId"

  private const val NO_TEAM = "none"
}

/**
 * The participants of the event, one card each, under a bar naming their team. Teams nobody has
 * joined yet are listed too, and the participants of no team come last.
 *
 * A tap on a card unfolds the actions its participant allows, and folds it again; one card is open
 * at a time. No action is offered while a change runs.
 *
 * @param onManagerAction called with a member and their team to make them its manager, or with its
 *   manager to stop it.
 * @param onMoveAction called with a member to move them to another team, or with a participant of
 *   no team to put them into one. Null leaves the move out of the cards; a card with no action left
 *   does not unfold.
 */
@Composable
fun ParticipantsList(
    state: ParticipantsUiState,
    onRetry: () -> Unit,
    onManagerAction: (ParticipantItem, Team) -> Unit,
    modifier: Modifier = Modifier,
    onMoveAction: ((ParticipantItem) -> Unit)? = null,
) {
  val colors = MaterialTheme.colorScheme
  var expanded by rememberSaveable { mutableStateOf<String?>(null) }
  // A participant gone from the list, e.g. after a reload, leaves no card open behind them.
  LaunchedEffect(state.groups, state.isLoading, state.error) {
    if (
        !state.isLoading &&
            state.error == null &&
            state.groups.none { group -> group.participants.any { it.userId == expanded } }
    ) {
      expanded = null
    }
  }
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
        val userId = participant.userId
        ParticipantCard(
            participant = participant,
            expanded = expanded == userId,
            enabled = !state.isUpdating,
            onToggle = { expanded = if (expanded == userId) null else userId },
            // An action folds the card: its dialog takes over, and the list moves on after it.
            onManagerAction =
                team?.let {
                  {
                    expanded = null
                    onManagerAction(participant, it)
                  }
                },
            onMoveAction =
                onMoveAction?.let {
                  {
                    expanded = null
                    it(participant)
                  }
                },
        )
      }
    }
  }
}

/**
 * The failure, in the words of the Requests tab, with a way to try again, except after a failed
 * change of manager or move: reloading would not retry it, and the list is still there to tap
 * again.
 */
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
    if (error != ParticipantsError.UPDATE_FAILED && error != ParticipantsError.MOVE_FAILED) {
      TextButton(
          onClick = onRetry,
          enabled = enabled,
          modifier = Modifier.testTag(ParticipantsListTestTags.RETRY_BUTTON),
      ) {
        Text(text = stringResource(R.string.participants_retry), fontWeight = FontWeight.SemiBold)
      }
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
              // One node, so the bar reads, and is found, as its whole text.
              .semantics(mergeDescendants = true) {}
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
 * One participant, drawn like the cards of the Requests tab: their initials, their name, and a
 * badge when they manage their team. The chevron says the card unfolds, and turns as it does; open,
 * the card is outlined in the accent and shows the actions its participant allows.
 *
 * @param onManagerAction null for a participant of no team, who has no team to manage.
 * @param onMoveAction null when the move is not offered; without either action, the card is only
 *   their name, and does not unfold.
 */
@Composable
private fun ParticipantCard(
    participant: ParticipantItem,
    expanded: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onManagerAction: (() -> Unit)?,
    onMoveAction: (() -> Unit)?,
) {
  val colors = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(16.dp)
  val name =
      participant.name?.takeIf { it.isNotBlank() }
          ?: stringResource(R.string.participants_unknown_volunteer)
  val border by
      animateColorAsState(
          if (expanded) colors.primary.copy(alpha = 0.7f) else colors.outlineVariant,
          label = "border",
      )
  val chevronTurn by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
  val unfolds = onManagerAction != null || onMoveAction != null
  val state =
      stringResource(
          if (expanded) R.string.participants_card_expanded
          else R.string.participants_card_collapsed
      )
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .clip(shape)
              .background(colors.surfaceVariant)
              .border(1.dp, border, shape)
  ) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.fillMaxWidth()
                .then(
                    if (unfolds) {
                      Modifier.clickable(
                          role = Role.Button,
                          onClickLabel =
                              stringResource(
                                  if (expanded) R.string.participants_hide_actions
                                  else R.string.participants_show_actions
                              ),
                          onClick = onToggle,
                      )
                    } else Modifier
                )
                .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp)
                // TalkBack says whether the card is open, which the chevron only shows.
                .semantics(mergeDescendants = true) { if (unfolds) stateDescription = state }
                .testTag(ParticipantsListTestTags.participantCard(participant.userId)),
    ) {
      Initials(participant.name, highlighted = expanded)
      Text(
          text = name,
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
      if (unfolds) {
        Chevron(expanded, chevronTurn)
      }
    }
    AnimatedVisibility(
        visible = expanded && unfolds,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
      Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
      ) {
        val moveTag = ParticipantsListTestTags.moveButton(participant.userId)
        val managerTag = ParticipantsListTestTags.managerButton(participant.userId)
        if (onManagerAction == null) {
          onMoveAction?.let {
            CardAction(
                R.string.participants_add_to_team,
                Icons.Rounded.SwapHoriz,
                ActionStyle.MAIN,
                enabled,
                moveTag,
                it,
            )
          }
        } else if (participant.isManager) {
          CardAction(
              R.string.participants_stop_managing,
              Icons.Rounded.PersonRemove,
              ActionStyle.WARNING,
              enabled,
              managerTag,
              onManagerAction,
          )
        } else {
          onMoveAction?.let {
            CardAction(
                R.string.participants_change_team,
                Icons.Rounded.SwapHoriz,
                ActionStyle.QUIET,
                enabled,
                moveTag,
                it,
            )
          }
          CardAction(
              R.string.participants_make_manager,
              Icons.Rounded.WorkspacePremium,
              ActionStyle.MAIN,
              enabled,
              managerTag,
              onManagerAction,
          )
        }
      }
    }
  }
}

/** The chevron that says a card unfolds, turned by [turn] degrees as it does. */
@Composable
private fun Chevron(expanded: Boolean, turn: Float) {
  val colors = MaterialTheme.colorScheme
  Box(
      contentAlignment = Alignment.Center,
      modifier =
          Modifier.size(32.dp)
              .clip(CircleShape)
              .background(if (expanded) colors.primaryContainer else colors.background),
  ) {
    Icon(
        imageVector = Icons.Rounded.ExpandMore,
        contentDescription = null,
        tint = if (expanded) colors.primary else colors.onSurfaceVariant,
        modifier = Modifier.rotate(turn),
    )
  }
}

/** The participant's initials in a disc, which takes the accent while their card is open. */
@Composable
private fun Initials(name: String?, highlighted: Boolean) {
  val colors = MaterialTheme.colorScheme
  Box(
      contentAlignment = Alignment.Center,
      modifier =
          Modifier.size(36.dp)
              .clip(CircleShape)
              .background(if (highlighted) colors.primary else colors.primaryContainer)
              .clearAndSetSemantics {},
  ) {
    Text(
        text = avatarInitials(name),
        color = if (highlighted) colors.onPrimary else colors.primary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
    )
  }
}

/** How an action of an open card stands out. */
private enum class ActionStyle {
  /** The main action, in the accent. */
  MAIN,
  /** A secondary one, on a quieter step. */
  QUIET,
  /** One that takes something away, in the error colour, like the delete of a team (#399). */
  WARNING,
}

/** One action of an open card, drawn in its [style]. */
@Composable
private fun RowScope.CardAction(
    @StringRes label: Int,
    icon: ImageVector,
    style: ActionStyle,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Button(
      onClick = onClick,
      enabled = enabled,
      shape = MaterialTheme.shapes.medium,
      contentPadding = PaddingValues(horizontal = 12.dp),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = if (style == ActionStyle.MAIN) colors.primary else colors.background,
              contentColor =
                  when (style) {
                    ActionStyle.MAIN -> colors.onPrimary
                    ActionStyle.QUIET -> colors.onSurface
                    ActionStyle.WARNING -> colors.error
                  },
          ),
      modifier = Modifier.weight(1f).heightIn(min = 44.dp).testTag(testTag),
  ) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Text(
        text = stringResource(label),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 8.dp),
    )
  }
}

private val ParticipantsError.message: Int
  get() =
      when (this) {
        ParticipantsError.OFFLINE -> R.string.participants_error_offline
        ParticipantsError.PERMISSION_DENIED -> R.string.participants_error_permission_denied
        ParticipantsError.UNEXPECTED -> R.string.participants_error_unexpected
        ParticipantsError.UPDATE_FAILED -> R.string.participants_manager_update_failed
        ParticipantsError.MOVE_FAILED -> R.string.participants_move_failed
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
        onManagerAction = { _, _ -> },
        onMoveAction = {},
    )
  }
}
