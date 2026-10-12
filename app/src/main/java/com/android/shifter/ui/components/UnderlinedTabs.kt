// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.ui.theme.shifter_divider
import com.swent.shifter.ui.theme.shifter_tabInactive

/** A row of [UnderlinedTab]s, followed by a full-width divider. */
@Composable
fun UnderlinedTabRow(modifier: Modifier = Modifier, tabs: @Composable RowScope.() -> Unit) {
  Column(modifier = modifier.fillMaxWidth()) {
    Row(modifier = Modifier.padding(horizontal = 24.dp), content = tabs)
    Box(Modifier.fillMaxWidth().height(1.dp).background(shifter_divider))
  }
}

/** A tab of an [UnderlinedTabRow]: its [label] above a bar, highlighted while [isSelected]. */
@Composable
fun UnderlinedTab(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    width: Dp,
    modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp),
      modifier =
          Modifier.width(width)
              .semantics { selected = isSelected }
              .clickable(role = Role.Tab, onClick = onClick)
              .padding(start = 16.dp, end = 16.dp, top = 8.dp)
              .then(modifier),
  ) {
    Text(
        text = label,
        color = if (isSelected) colors.onBackground else shifter_tabInactive,
        fontSize = 14.sp,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
    )
    Box(
        Modifier.fillMaxWidth()
            .height(2.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(if (isSelected) colors.primary else shifter_divider)
    )
  }
}
