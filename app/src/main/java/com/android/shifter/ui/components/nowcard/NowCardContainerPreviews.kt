// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.nowcard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.shifter.ui.components.PrimaryButton
import com.swent.shifter.ui.theme.ShifterTheme

// Previews of NowCardContainer, kept apart from the card itself: they are tooling the IDE renders
// and no test ever runs, so coverage is told to leave this file alone.

/** The four tones one above the other, with sample content like the Figma frames. */
@Preview(showBackground = true, backgroundColor = 0xFF151D27, heightDp = 1100)
@Composable
private fun NowCardContainerTonesPreview() {
  ShifterTheme {
    Column(
        modifier =
            Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp).width(358.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      NowCardContainer(
          tone = NowTone.CALM,
          status = "Next mission",
          headline = "At 14:00",
          state = "Food sorting",
      ) {
        SampleMission(it)
      }
      NowCardContainer(
          tone = NowTone.SOON,
          status = "Starting soon",
          headline = "In 20 min",
          state = "Food sorting",
      ) {
        SampleMission(it)
        PrimaryButton(text = "View on map", onClick = {}, testTag = "")
      }
      NowCardContainer(
          tone = NowTone.ALERT,
          status = "Not checked in",
          headline = "Started 6 min ago",
          state = "Food sorting",
      ) {
        SampleMission(it)
      }
      NowCardContainer(
          tone = NowTone.LIVE,
          status = "Now",
          headline = "15 min left",
          state = "Food sorting",
          strip = {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                        .padding(20.dp)
            ) {
              Text(
                  text = "Then, in 30 min",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(text = "Welcome desk · Main gate", color = MaterialTheme.colorScheme.onSurface)
            }
          },
      ) {
        SampleMission(it)
      }
      NowCardContainer(
          tone = NowTone.CALM,
          status = "Today",
          headline = "You're done for today",
          state = Unit,
      )
    }
  }
}

@Composable
private fun SampleMission(title: String) {
  Column {
    Text(
        text = title,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = "14:00–15:30 · Hall B",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
