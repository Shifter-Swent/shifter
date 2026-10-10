// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.ui.membership.MembershipRequestUIState
import com.swent.shifter.ui.membership.MembershipRequestsScreen
import com.swent.shifter.ui.navigation.OrganizerTabs
import com.swent.shifter.ui.navigation.Tab
import com.swent.shifter.ui.theme.ShifterTheme
import com.swent.shifter.ui.theme.shifter_divider
import com.swent.shifter.ui.theme.shifter_tabInactive

/** People navigation shell. Participants content will be added separately. */
@Composable
fun PeopleScreen(
    uiState: MembershipRequestUIState,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    onRetry: () -> Unit,
    onNavigate: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  var selectedTab by rememberSaveable { mutableIntStateOf(0) }
  Scaffold(
      modifier = modifier,
      containerColor = colors.background,
      topBar = { PeopleTopBar() },
      bottomBar = { OrganizerNavigationBar(onNavigate) },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      PeopleTabs(
          labels =
              listOf(
                  stringResource(R.string.people_tab_participants),
                  stringResource(R.string.people_tab_requests),
              ),
          selected = selectedTab,
          onSelect = { selectedTab = it },
      )
      if (selectedTab == 0) {
        Text(
            text = stringResource(R.string.people_participants_placeholder),
            color = colors.onSurfaceVariant,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(24.dp),
        )
      } else {
        MembershipRequestsScreen(uiState, onAccept, onReject, onRetry, Modifier.weight(1f))
      }
    }
  }
}

/** "People" title, laid out like the My Events header. */
@Composable
private fun PeopleTopBar() {
  Text(
      text = stringResource(R.string.people_title),
      color = MaterialTheme.colorScheme.onBackground,
      fontSize = 22.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
  )
}

/** Underlined tabs followed by the full-width divider, like the My Events tabs. */
@Composable
private fun PeopleTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
  val colors = MaterialTheme.colorScheme
  Column(Modifier.fillMaxWidth()) {
    Row(Modifier.padding(horizontal = 24.dp)) {
      labels.forEachIndexed { index, label ->
        val isSelected = index == selected
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier =
                Modifier.width(150.dp)
                    .semantics { this.selected = isSelected }
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp),
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
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(shifter_divider))
  }
}

@Composable
private fun OrganizerNavigationBar(onNavigate: (Tab) -> Unit) {
  val colors = MaterialTheme.colorScheme
  NavigationBar(containerColor = colors.surfaceContainer) {
    OrganizerTabs.all.forEach { tab ->
      NavigationBarItem(
          selected = tab == OrganizerTabs.People,
          onClick = { onNavigate(tab) },
          icon = { Icon(tab.icon, contentDescription = null) },
          label = { Text(tab.label, fontSize = 11.sp) },
          colors =
              NavigationBarItemDefaults.colors(
                  selectedIconColor = colors.primary,
                  selectedTextColor = colors.primary,
                  indicatorColor = colors.primaryContainer,
                  unselectedIconColor = shifter_tabInactive,
                  unselectedTextColor = shifter_tabInactive,
              ),
          modifier = Modifier.testTag(tab.testTag),
      )
    }
  }
}

@Preview
@Composable
private fun PeopleScreenPreview() {
  ShifterTheme { PeopleScreen(MembershipRequestUIState(isLoading = false), {}, {}, {}, {}) }
}
