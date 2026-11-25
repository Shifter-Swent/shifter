// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import com.swent.shifter.model.time.eventDay
import com.swent.shifter.model.time.formatDayOfWeek
import java.time.Instant
import java.time.ZoneId

/*
 * What MissionAssignedFormat and MissionChangedFormat share. The data they read is built by the
 * writers, in model/mission/MissionNotifications.kt.
 */

/** The short day of the week of this instant in [zone], "Sat", written before a time. */
internal fun Instant.formatDayOfWeek(zone: ZoneId): String =
    atZone(zone).toLocalDate().formatDayOfWeek()

/**
 * The Planning a tap opens for a notification about [line]: on the event day the mission starts,
 * which for a mission at 02:00 is the day before (see [NotificationTarget.Planning]).
 */
internal fun planningOf(
    eventId: String,
    eventTitle: String,
    line: MissionLine,
    zone: ZoneId,
): NotificationTarget.Planning =
    NotificationTarget.Planning(eventId, eventTitle, line.startAt.eventDay(zone).toString())
