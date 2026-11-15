// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.nowcard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

// Previews of the time bars, kept apart from them: they are tooling the IDE renders and no test
// ever runs, so coverage is told to leave this file alone.

private val START = Instant.parse("2026-11-14T14:00:00Z")
private val END = Instant.parse("2026-11-14T15:30:00Z")

/** The bars of the Figma frames: soon, late, ongoing in its last hour, and a manager's bar. */
@Preview(showBackground = true, backgroundColor = 0xFF192337)
@Composable
private fun TimeBarsPreview() {
  ShifterTheme {
    Column(
        modifier =
            Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(20.dp)
                .width(318.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      CountdownBar(
          start = START,
          now = START.minus(Duration.ofMinutes(20)),
          window = Duration.ofMinutes(45),
          color = NowTone.SOON.accent,
          zone = ZoneOffset.UTC,
      )
      TimeBar(START, END, START.plusSeconds(6 * 60), NowTone.ALERT.accent, ZoneOffset.UTC)
      TimeBar(START, END, END.minus(Duration.ofMinutes(50)), NowTone.LIVE.accent, ZoneOffset.UTC)
      TimeBar(
          from = START,
          to = END,
          now = END.minus(Duration.ofMinutes(10)),
          color = NowTone.LIVE.accent,
          zone = ZoneOffset.UTC,
          title = "Food sorting",
      )
    }
  }
}
