// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.map

import com.swent.shifter.model.location.GeoPosition

/**
 * A pin shown by [ShifterMap]. It holds a [GeoPosition] rather than a Maps `LatLng`, so callers
 * (missions, events, a location picker...) don't depend on the Maps SDK.
 *
 * @property id identifies the marker; it must be unique within the list given to [ShifterMap].
 * @property info optional text shown under the title when the marker is tapped.
 * @property caption optional short line shown above the title, such as when the next mission
 *   starts.
 * @property highlighted whether the marker stands out from the others, drawn in the accent colour
 *   and above them where they overlap.
 */
data class MapMarker(
    val id: String,
    val position: GeoPosition,
    val title: String,
    val info: String? = null,
    val caption: String? = null,
    val highlighted: Boolean = false,
)
