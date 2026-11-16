// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.swent.shifter.R

/**
 * A list the user reloads by pulling it down, with Material's spinner over the top of [content]
 * while the reload they asked for runs. The pull needs a scrollable [content], such as a
 * `LazyColumn`, even when it holds only a message.
 *
 * The spinner shows only for a reload the user asked for, and only over [content] worth keeping: an
 * automatic reload, e.g. on coming back to the screen, stays silent, and a screen with nothing yet
 * shows its own spinner instead. A pull while a load runs waits for it rather than starting it
 * again, so a slow first load is never restarted.
 *
 * Screen readers cannot pull: the items of [content] mark a focusable node of theirs with
 * [refreshAction], which offers them a "Refresh" action instead.
 *
 * @param isLoading whether the screen is loading, by a pull or otherwise.
 * @param hasContent whether [content] shows something to keep while it reloads.
 * @param onRefresh reloads, e.g. the ViewModel's `refresh`.
 */
@Composable
fun RefreshableList(
    isLoading: Boolean,
    hasContent: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
  var pulled by remember { mutableStateOf(false) }
  // Over once the load ends. Not keyed on the pull: the screen may see the load it starts a frame
  // later, and the spinner must not be dropped meanwhile.
  LaunchedEffect(isLoading) { if (!isLoading) pulled = false }
  val loading by rememberUpdatedState(isLoading)
  val latestOnRefresh by rememberUpdatedState(onRefresh)
  val refresh = remember {
    {
      pulled = true
      if (!loading) latestOnRefresh()
    }
  }
  PullToRefreshBox(
      isRefreshing = pulled && isLoading && hasContent,
      onRefresh = refresh,
      modifier = modifier,
  ) {
    CompositionLocalProvider(LocalRefreshAction provides refresh) { content() }
  }
}

/** The reload of the [RefreshableList] around, for [refreshAction]; null outside of one. */
private val LocalRefreshAction = compositionLocalOf<(() -> Unit)?> { null }

/**
 * Offers screen readers the "Refresh" action of the [RefreshableList] around, which they cannot
 * pull. Put it on a node they focus, such as the title of each item: an action on a node they do
 * not focus is never offered. Does nothing outside of a [RefreshableList].
 */
fun Modifier.refreshAction(): Modifier = composed {
  val refresh = LocalRefreshAction.current ?: return@composed Modifier
  val label = stringResource(R.string.refresh)
  semantics {
    customActions =
        listOf(
            CustomAccessibilityAction(label) {
              refresh()
              true
            }
        )
  }
}
