// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events

import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventStatus
import com.swent.shifter.model.event.EventType
import java.time.Instant

/** An event of the organizer "organizer", in preparation, on Sat 14 Jun 2025 08:00–14:00 Paris. */
internal fun testEvent(
    id: String = "marathon",
    startAt: String = "2025-06-14T06:00:00Z",
    endAt: String = "2025-06-14T12:00:00Z",
    status: EventStatus = EventStatus.PREPARATION,
) =
    Event(
        id = id,
        organizerId = "organizer",
        title = "City Marathon",
        description = "",
        type = EventType.SPORT,
        startAt = Instant.parse(startAt),
        endAt = Instant.parse(endAt),
        location = EventLocation("Lyon, Place Bellecour"),
        status = status,
        createdAt = Instant.EPOCH,
    )
