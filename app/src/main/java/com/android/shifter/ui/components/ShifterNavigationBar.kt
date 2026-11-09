// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swent.shifter.ui.navigation.NavigationTestTags
import com.swent.shifter.ui.navigation.Tab

private val BarShape = RoundedCornerShape(32.dp)
private val ItemShape = RoundedCornerShape(24.dp)
private val TOUCH_HEIGHT = 64.dp
private val HIGHLIGHT_HEIGHT = 60.dp

/**
 * The floating bar of an event's tabs. Stateless: the caller says which of [tabs] is [selected],
 * and moves when [onSelect] reports a tap.
 */
@Composable
fun ShifterNavigationBar(
    tabs: List<Tab>,
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  Surface(
      modifier =
          modifier
              .fillMaxWidth()
              .navigationBarsPadding()
              .padding(horizontal = 12.dp, vertical = 8.dp)
              .testTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU),
      shape = BarShape,
      color = colors.surfaceContainer,
      border = BorderStroke(0.5.dp, colors.outline),
      shadowElevation = 6.dp,
  ) {
    Row(
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      tabs.forEach { tab ->
        NavigationItem(
            tab = tab,
            isSelected = tab == selected,
            onClick = { onSelect(tab) },
            modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun NavigationItem(
    tab: Tab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val background by
      animateColorAsState(if (isSelected) colors.primaryContainer else Color.Transparent)
  val interactionSource = remember { MutableInteractionSource() }
  // The touch target is taller than the highlight, so the two are separate.
  Box(
      modifier =
          modifier
              .selectable(
                  selected = isSelected,
                  interactionSource = interactionSource,
                  indication = null,
                  role = Role.Tab,
                  onClick = onClick,
              )
              .testTag(tab.testTag)
              .heightIn(min = TOUCH_HEIGHT),
      contentAlignment = Alignment.Center,
  ) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = HIGHLIGHT_HEIGHT)
                .clip(ItemShape)
                .background(background)
                .indication(interactionSource, ripple())
                .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
      Icon(
          imageVector = tab.icon,
          contentDescription = null,
          tint = if (isSelected) colors.primary else colors.onSurfaceVariant,
      )
      Spacer(Modifier.height(4.dp))
      Text(
          text = tab.label,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
          color = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
