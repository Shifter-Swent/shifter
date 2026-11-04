// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.ui.theme.ShifterTheme

// Previews of VolunteerLabel, kept apart from the screen: they are tooling the IDE renders and no
// test ever runs, so coverage is told to leave this file alone.

private val HERE = GeoPosition(46.52, 6.57)

/** A position just shared, one a few minutes old, one greyed out, and a volunteer with no name. */
@Preview(showBackground = true, backgroundColor = 0xFF151D27)
@Composable
private fun VolunteerLabelPreview() {
  ShifterTheme {
    Column(
        modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      VolunteerLabel(MapMarker("volunteer-1", HERE, "Alice", volunteerAge(0)), isStale = false)
      VolunteerLabel(MapMarker("volunteer-2", HERE, "Bob", volunteerAge(4)), isStale = false)
      VolunteerLabel(MapMarker("volunteer-3", HERE, "Carol", volunteerAge(17)), isStale = true)
      VolunteerLabel(MapMarker("volunteer-4", HERE, "Volunteer", volunteerAge(1)), isStale = false)
    }
  }
}
