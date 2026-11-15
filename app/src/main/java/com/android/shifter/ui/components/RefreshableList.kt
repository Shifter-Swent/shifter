// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.swent.shifter.R

/**
 * A list the user reloads by pulling it down, with Material's spinner over the top of [content]
 * while it reloads. The pull needs a scrollable [content], such as a `LazyColumn`, even when it
 * holds only a message. Screen readers get a "Refresh" action instead of the gesture.
 *
 * @param isRefreshing true only while content is shown and reloads: a screen that shows its own
 *   spinner when it has nothing yet passes false then, so the two never show together.
 * @param onRefresh reloads, e.g. the ViewModel's `refresh`.
 */
@Composable
fun RefreshableList(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
  val refresh = stringResource(R.string.refresh)
  PullToRefreshBox(
      isRefreshing = isRefreshing,
      onRefresh = onRefresh,
      modifier =
          modifier.semantics {
            customActions =
                listOf(
                    CustomAccessibilityAction(refresh) {
                      onRefresh()
                      true
                    }
                )
          },
      content = content,
  )
}
