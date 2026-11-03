// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import java.time.Instant

/**
 * The last position a volunteer shared with their manager, while their location sharing is on. A
 * volunteer has at most one per team: see [LiveLocationRepository] for the rules.
 *
 * The model stays free of Firebase and Android types so it can be used by ViewModels and tested
 * without a backend.
 *
 * @property userId id of the volunteer the position belongs to.
 * @property position where they were, as read from the device.
 * @property measuredAt when the GPS measured [position], rather than when it was stored, so a
 *   reader can tell how old it is.
 */
data class LiveLocation(val userId: String, val position: GeoPosition, val measuredAt: Instant)
