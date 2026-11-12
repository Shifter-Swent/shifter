// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import android.content.ClipData
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.ui.theme.ShifterTheme
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

object JoinCodeCardTestTags {
  const val CODE = "JoinCodeCardCode"
  const val COPY_BUTTON = "JoinCodeCardCopyButton"
  const val UNAVAILABLE = "JoinCodeCardUnavailable"
  const val RETRY_BUTTON = "JoinCodeCardRetryButton"
}

/** Shown in place of a code still being read, so the card already has its final size. */
const val JOIN_CODE_PLACEHOLDER = "······"

/** How much room [JoinCodeCard] takes on its screen. */
enum class JoinCodeCardSize {
  /** The code in large type, for the screen whose purpose is to show it. */
  Normal,
  /** The code on one line, above the rest of a screen. */
  Compact,
}

/**
 * An event's join code in a highlighted card, with a button to copy it. The same card wherever the
 * code is shown, so the organizer always recognizes it.
 *
 * @param onCopy called when the copy button is tapped; [rememberCopyJoinCode] does the copy.
 * @param copyEnabled whether there is a code to copy, false while [JOIN_CODE_PLACEHOLDER] is shown.
 */
@Composable
fun JoinCodeCard(
    joinCode: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    size: JoinCodeCardSize = JoinCodeCardSize.Normal,
    copyEnabled: Boolean = true,
) {
  val colorScheme = MaterialTheme.colorScheme
  val shape = MaterialTheme.shapes.medium
  val cardModifier = modifier.cardStyle()
  when (size) {
    JoinCodeCardSize.Normal ->
        Column(
            modifier = cardModifier.padding(horizontal = 12.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Label(R.string.event_created_code_label)
          Code(joinCode, large = true)
          OutlinedButton(
              onClick = onCopy,
              enabled = copyEnabled,
              shape = shape,
              modifier = Modifier.testTag(JoinCodeCardTestTags.COPY_BUTTON),
          ) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = null, Modifier.size(18.dp))
            Text(
                text = stringResource(R.string.event_join_code_copy),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp),
            )
          }
        }
    JoinCodeCardSize.Compact ->
        CompactCard(modifier, line = { Code(joinCode, large = false) }) {
          IconButton(
              onClick = onCopy,
              enabled = copyEnabled,
              // Given to the button, not the icon, so a disabled copy is greyed out.
              colors = IconButtonDefaults.iconButtonColors(contentColor = colorScheme.primary),
              modifier = Modifier.testTag(JoinCodeCardTestTags.COPY_BUTTON),
          ) {
            Icon(
                Icons.Outlined.ContentCopy,
                contentDescription = stringResource(R.string.event_join_code_copy),
            )
          }
        }
  }
}

/**
 * The compact [JoinCodeCard] when the code could not be read, or the event has none yet: says so,
 * with a button to read it again, in the same place and look so nothing moves.
 */
@Composable
fun JoinCodeUnavailableCard(onRetry: () -> Unit, modifier: Modifier = Modifier) {
  CompactCard(
      modifier,
      line = {
        Text(
            text = stringResource(R.string.event_join_code_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(JoinCodeCardTestTags.UNAVAILABLE),
        )
      },
  ) {
    IconButton(
        onClick = onRetry,
        colors =
            IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.testTag(JoinCodeCardTestTags.RETRY_BUTTON),
    ) {
      Icon(
          Icons.Outlined.Refresh,
          contentDescription = stringResource(R.string.event_join_code_retry),
      )
    }
  }
}

/**
 * The layout every compact card shares, so a card never changes size from one state to another: the
 * label, then [line] in a slot as tall as the code, then [action], an icon button.
 */
@Composable
private fun CompactCard(
    modifier: Modifier,
    line: @Composable () -> Unit,
    action: @Composable () -> Unit,
) {
  Row(
      modifier = modifier.cardStyle().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Label(R.string.event_join_code_label_short)
      Box(contentAlignment = Alignment.CenterStart) {
        // An invisible code holds the line at the code's exact height, whatever the font scale and
        // whatever [line] shows. Hidden from screen readers and tests.
        Box(Modifier.alpha(0f).clearAndSetSemantics {}) {
          Code(JOIN_CODE_PLACEHOLDER, large = false)
        }
        line()
      }
    }
    action()
  }
}

/** The highlighted background and border every join code card shares. */
@Composable
private fun Modifier.cardStyle(): Modifier {
  val colorScheme = MaterialTheme.colorScheme
  val shape = MaterialTheme.shapes.medium
  return fillMaxWidth()
      .clip(shape)
      .background(colorScheme.primaryContainer)
      .border(1.dp, colorScheme.primary, shape)
}

@Composable
private fun Label(@StringRes text: Int) {
  Text(
      text = stringResource(text),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/**
 * The code in monospace, spaced out so each character reads on its own. While it is
 * [JOIN_CODE_PLACEHOLDER], screen readers say the code is loading instead of reading out the dots.
 */
@Composable
private fun Code(joinCode: String, large: Boolean) {
  val loading = stringResource(R.string.event_join_code_loading)
  Text(
      text = joinCode,
      style =
          if (large) MaterialTheme.typography.displayMedium
          else MaterialTheme.typography.titleLarge,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Bold,
      letterSpacing = if (large) 6.sp else 4.sp,
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center,
      modifier =
          Modifier.testTag(JoinCodeCardTestTags.CODE)
              .then(
                  if (joinCode == JOIN_CODE_PLACEHOLDER)
                      Modifier.clearAndSetSemantics { contentDescription = loading }
                  else Modifier
              ),
  )
}

/**
 * Returns what copies a join code: it puts the code in the clipboard, then confirms it in
 * [snackbarHostState], or says the copy failed, e.g. on a device refusing access to its clipboard,
 * so the organizer knows to copy it by hand. The copy stays in the UI, so no ViewModel deals with
 * the clipboard; a test replaces it by providing its own `LocalClipboard`.
 */
@Composable
fun rememberCopyJoinCode(snackbarHostState: SnackbarHostState): (String) -> Unit {
  val clipboard = LocalClipboard.current
  val scope = rememberCoroutineScope()
  val clipLabel = stringResource(R.string.event_join_code_label_short)
  val copied = stringResource(R.string.event_join_code_copied)
  val failed = stringResource(R.string.event_join_code_copy_failed)
  return remember(clipboard, scope, snackbarHostState, clipLabel, copied, failed) {
    { joinCode ->
      scope.launch {
        val message =
            try {
              clipboard.setClipEntry(ClipData.newPlainText(clipLabel, joinCode).toClipEntry())
              copied
            } catch (e: CancellationException) {
              throw e
            } catch (_: Exception) {
              failed
            }
        // A second copy replaces the message rather than queuing another one behind it.
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(message)
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun JoinCodeCardPreview() {
  ShifterTheme {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      JoinCodeCard(joinCode = "ABC234", onCopy = {})
      JoinCodeCard(joinCode = "ABC234", onCopy = {}, size = JoinCodeCardSize.Compact)
      JoinCodeCard(
          joinCode = JOIN_CODE_PLACEHOLDER,
          onCopy = {},
          size = JoinCodeCardSize.Compact,
          copyEnabled = false,
      )
      JoinCodeUnavailableCard(onRetry = {})
    }
  }
}
