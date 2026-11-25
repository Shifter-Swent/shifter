// Co-authored-by: Claude Opus 5 (1M context) <noreply@anthropic.com>
package com.swent.shifter.ui.shifts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.model.shift.ShiftSlot
import com.swent.shifter.model.time.formatDayOfWeek
import com.swent.shifter.model.time.formatTime
import com.swent.shifter.ui.components.ShifterTopBar
import com.swent.shifter.ui.components.shifts.ShiftGrid
import com.swent.shifter.ui.components.shifts.ShiftGridMode
import com.swent.shifter.ui.theme.shifter_mutedText
import java.time.ZoneId

object ShiftsScreenTestTags {
  const val SCREEN = "ShiftsScreen"
  const val EDIT = "ShiftsScreen.EDIT"
  const val DONE = "ShiftsScreen.DONE"
  const val EMPTY = "ShiftsScreen.EMPTY"
  const val LOADING = "ShiftsScreen.LOADING"
  const val LOAD_FAILED = "ShiftsScreen.LOAD_FAILED"
  const val MESSAGE = "ShiftsScreen.MESSAGE"
  const val LOCKED_MESSAGE = "ShiftsScreen.LOCKED_MESSAGE"
  const val SAVING = "ShiftsScreen.SAVING"
}

/**
 * The organizer's Shifts screen: the 2 h slots of the event in the week grid of #598, read in View
 * and opened or closed in Edit, each tap saved at once by [ShiftsViewModel].
 *
 * @param zone the zone the slots and the messages are read in, the phone's, the one
 *   [ShiftsViewModel] uses by default too, so a cell and the message naming it always agree.
 */
@Composable
fun ShiftsScreen(
    viewModel: ShiftsViewModel,
    onBack: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
  val uiState by viewModel.uiState.collectAsState()
  ShiftsContent(
      state = uiState,
      onBack = onBack,
      onSetMode = viewModel::setMode,
      onToggleSlot = viewModel::toggleSlot,
      onMessageShown = viewModel::messageShown,
      zone = zone,
  )
}

/**
 * Stateless form of [ShiftsScreen]: it renders [state] and reports every interaction.
 *
 * The grid is only offered once the shifts have arrived. A read that failed with nothing to show
 * says so rather than passing for an event without a slot, and one that failed over slots already
 * read keeps them, frozen, behind its message.
 */
@Composable
fun ShiftsContent(
    state: ShiftsUiState,
    onBack: () -> Unit,
    onSetMode: (ShiftsMode) -> Unit,
    onToggleSlot: (ShiftSlot) -> Unit,
    onMessageShown: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
  val snackbarHostState = remember { SnackbarHostState() }
  val kept = state.keptSlot?.let { KeptMessage(it.keptTitle(zone), it.keptReason()) }
  val error = state.error?.let { stringResource(it.message) }
  // The state can hold both at once, and messageShown() clears both: each is shown before either
  // is reported, and a newer message cancels this effect rather than being dropped by it.
  LaunchedEffect(kept, error) {
    if (kept != null || error != null) {
      if (kept != null) snackbarHostState.showSnackbar(kept)
      if (error != null) snackbarHostState.showSnackbar(error)
      onMessageShown()
    }
  }
  val editing = state.mode == ShiftsMode.EDIT
  Scaffold(
      topBar = { ShifterTopBar(stringResource(R.string.shifts_title), onBack) },
      snackbarHost = { MessageHost(snackbarHostState) },
      // The empty state offers its one action at the bottom, where the design has it.
      bottomBar = {
        if (state.showsEmpty) OpenSlotsButton(state.canEdit) { onSetMode(ShiftsMode.EDIT) }
      },
  ) { padding ->
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(padding)
                // The twelve rows of Edit are taller than a phone: the whole grid is reachable by
                // scrolling down, as its own sideways scroll reaches the days.
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 10.dp)
                .testTag(ShiftsScreenTestTags.SCREEN),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Header(state, editing, onSetMode)
      when {
        state.isLoading ->
            Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), Alignment.Center) {
              CircularProgressIndicator(modifier = Modifier.testTag(ShiftsScreenTestTags.LOADING))
            }
        // Nothing was ever read, so there is no grid to show and no empty event to claim.
        state.shiftsFailed && state.shifts.isEmpty() ->
            Notice(
                stringResource(R.string.shifts_load_failed_title),
                stringResource(R.string.shifts_load_failed_body),
                ShiftsScreenTestTags.LOAD_FAILED,
            )
        state.showsEmpty ->
            Notice(
                stringResource(R.string.shifts_empty_title),
                stringResource(R.string.shifts_empty_body),
                ShiftsScreenTestTags.EMPTY,
            )
        else ->
            ShiftGrid(
                days = state.days,
                shifts = state.shifts,
                // Edit is drawn only while the ViewModel would act on a tap; Done still
                // leaves it, so the organizer is never stuck in a mode that does nothing.
                mode = if (editing && state.canEdit) ShiftGridMode.EDIT else ShiftGridMode.VIEW,
                zone = zone,
                period = state.period,
                savingShiftIds = state.savingShiftIds,
                onSlot = onToggleSlot,
            )
      }
    }
  }
}

/**
 * Whether the screen shows its empty state: a read answered with no slot, Edit is not on, and no
 * write is in flight. Closing the last slot offline empties the grid at once while the delete is
 * still pending, which "No shifts yet" would read as settled.
 */
private val ShiftsUiState.showsEmpty: Boolean
  get() = isEmpty && mode != ShiftsMode.EDIT && savingShiftIds.isEmpty()

/**
 * The slot a refused close kept, as its own snackbar, so the card keeps the words it was shown with
 * whatever the state holds by then.
 */
private data class KeptMessage(val title: String, val reason: String) : SnackbarVisuals {
  override val message = "$title · $reason"
  override val actionLabel = null
  override val withDismissAction = false
  override val duration = SnackbarDuration.Short
}

/** "Sat 10:00 stays", the first line of the card and the start of the sentence read out. */
@Composable
private fun KeptSlot.keptTitle(zone: ZoneId): String =
    stringResource(
        R.string.shifts_kept_title,
        "${startAt.atZone(zone).toLocalDate().formatDayOfWeek()} ${startAt.formatTime(zone)}",
    )

/** "3 volunteers chose it.", its second line. */
@Composable
private fun KeptSlot.keptReason(): String =
    pluralStringResource(R.plurals.shifts_kept_reason, volunteers, volunteers)

private val ShiftsError.message: Int
  get() =
      when (this) {
        ShiftsError.OFFLINE -> R.string.shifts_error_offline
        ShiftsError.SAVE_FAILED -> R.string.shifts_error_save
        ShiftsError.LOAD_FAILED -> R.string.shifts_error_load
      }

/** The snackbar of the last message: a kept slot takes the lock and two lines of the design. */
@Composable
private fun MessageHost(hostState: SnackbarHostState) {
  SnackbarHost(hostState, modifier = Modifier.testTag(ShiftsScreenTestTags.MESSAGE)) { data ->
    Snackbar(modifier = Modifier.padding(12.dp)) {
      when (val visuals = data.visuals) {
        is KeptMessage ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.testTag(ShiftsScreenTestTags.LOCKED_MESSAGE),
            ) {
              Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
              Column {
                Text(visuals.title, fontWeight = FontWeight.SemiBold)
                Text(visuals.reason, style = MaterialTheme.typography.bodySmall)
              }
            }
        else -> Text(visuals.message)
      }
    }
  }
}

/** The total hours open and what the slots are for, with the action that switches the mode. */
@Composable
private fun Header(state: ShiftsUiState, editing: Boolean, onSetMode: (ShiftsMode) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
          text =
              if (state.showsEmpty) stringResource(R.string.shifts_title)
              else
                  stringResource(
                      R.string.shifts_open_total,
                      state.openHours,
                      pluralStringResource(R.plurals.shifts_days, state.days.size, state.days.size),
                  ),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground,
      )
      Text(
          text =
              stringResource(
                  if (state.showsEmpty) R.string.shifts_empty_subtitle else R.string.shifts_subtitle
              ),
          style = MaterialTheme.typography.bodySmall,
          color = shifter_mutedText,
      )
    }
    if (state.savingShiftIds.isNotEmpty()) {
      CircularProgressIndicator(
          strokeWidth = 2.dp,
          modifier = Modifier.size(16.dp).testTag(ShiftsScreenTestTags.SAVING),
      )
    }
    // The empty state carries its own "Open slots" at the bottom instead.
    if (state.showsEmpty) return@Row
    if (editing) {
      Button(
          onClick = { onSetMode(ShiftsMode.VIEW) },
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.testTag(ShiftsScreenTestTags.DONE),
      ) {
        Text(stringResource(R.string.shifts_done), fontWeight = FontWeight.SemiBold)
      }
    } else {
      TextButton(
          onClick = { onSetMode(ShiftsMode.EDIT) },
          enabled = state.canEdit,
          modifier = Modifier.testTag(ShiftsScreenTestTags.EDIT),
      ) {
        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
        Text(
            text = stringResource(R.string.shifts_edit),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp),
        )
      }
    }
  }
}

/** The card of a state with no grid to draw: no slot open yet, or a read that failed. */
@Composable
private fun Notice(title: String, body: String, testTag: String) {
  val colors = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(18.dp)
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .background(colors.surfaceVariant, shape)
              .border(1.dp, colors.outline, shape)
              .padding(vertical = 28.dp, horizontal = 24.dp)
              .testTag(testTag),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
        modifier = Modifier.size(48.dp).background(colors.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          Icons.Outlined.Schedule,
          contentDescription = null,
          tint = colors.primary,
          modifier = Modifier.size(24.dp),
      )
    }
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = colors.onSurface,
    )
    Text(
        text = body,
        style = MaterialTheme.typography.bodySmall,
        color = shifter_mutedText,
        textAlign = TextAlign.Center,
    )
  }
}

/** The one action of the empty state, which goes to Edit so the first slots can be opened. */
@Composable
private fun OpenSlotsButton(enabled: Boolean, onClick: () -> Unit) {
  Button(
      onClick = onClick,
      enabled = enabled,
      shape = RoundedCornerShape(14.dp),
      modifier =
          Modifier.fillMaxWidth()
              .navigationBarsPadding()
              .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
              .height(48.dp)
              .testTag(ShiftsScreenTestTags.EDIT),
  ) {
    Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
    Text(
        text = stringResource(R.string.shifts_open_slots),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 8.dp),
    )
  }
}
