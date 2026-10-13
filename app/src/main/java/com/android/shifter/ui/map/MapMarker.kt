// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.map

/**
 * A pin shown by [ShifterMap]. It holds plain coordinates rather than a Maps `LatLng`, so callers
 * (missions, events, a location picker...) don't depend on the Maps SDK.
 *
 * @property id identifies the marker; it must be unique within the list given to [ShifterMap].
 * @property info optional text shown under the title when the marker is tapped.
 */
data class MapMarker(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val info: String? = null,
)
