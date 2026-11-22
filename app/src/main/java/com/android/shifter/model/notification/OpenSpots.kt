// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import com.swent.shifter.R
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionRepository
import com.swent.shifter.model.mission.isCompleted
import com.swent.shifter.model.time.formatTime
import com.swent.shifter.model.user.UserMode
import java.time.Duration
import java.time.Instant

/** How long before its start a mission short of volunteers is notified. */
private val OPEN_SPOTS_WINDOW: Duration = Duration.ofHours(24)

/**
 * Whether this mission is short of volunteers at [now] in a way worth telling its organizer: it
 * starts within the next 24 h, both ends included, so one already started is left out, it is not
 * completed, and it has fewer assignees than it needs.
 */
private fun Mission.isShortAt(now: Instant): Boolean =
    startAt >= now &&
        startAt <= now + OPEN_SPOTS_WINDOW &&
        !isCompleted(completed, endAt, now) &&
        assigneeIds.size < volunteersNeeded

/**
 * The OPEN_SPOTS check: tells an organizer, once per mission, which missions of their events start
 * within 24 h and still need volunteers, such as "Set up the stands at 08:00 still needs 2
 * volunteers.". A mission no longer short, full again, started or completed, is forgotten, so it is
 * told again if it becomes short again.
 *
 * It asks no [NotificationScheduler]: the engine (#614) runs the checks at least every 15 minutes,
 * which is soon enough for a mission a day ahead.
 */
class OpenSpots(
    private val events: EventRepository,
    private val missions: MissionRepository,
    private val texts: NotificationTexts,
) : TimedCheck {

  override val kind = NotificationKind.OPEN_SPOTS

  override suspend fun run(userId: String, now: Instant, seen: SeenStore): List<AppNotification> {
    // Nothing is marked until every notification is built, so a read or a text that fails loses
    // none. An event already over has no mission left to staff, so its missions are not read.
    val missionsByEvent =
        events
            .getEventsByOrganizer(userId)
            .filter { it.endAt >= now }
            .map { it to missions.getMissionsByEvent(it.id) }
    val shown = missionsByEvent.flatMap { (event, eventMissions) ->
      eventMissions.mapNotNull { mission ->
        when {
          // Forgetting early can at worst show a mission twice, never lose one.
          !mission.isShortAt(now) -> {
            seen.forget(mission.id)
            null
          }
          seen.wasShown(mission.id) -> null
          else -> notification(event.id, event.title, mission, now)
        }
      }
    }
    shown.forEach { seen.markShown(it.key) }
    return shown
  }

  private fun notification(
      eventId: String,
      eventTitle: String,
      mission: Mission,
      now: Instant,
  ): AppNotification {
    val missing = mission.volunteersNeeded - mission.assigneeIds.size
    return AppNotification(
        kind = kind,
        key = mission.id,
        title = eventTitle,
        text =
            texts.plural(
                R.plurals.notification_open_spots_text,
                missing,
                mission.title,
                mission.startAt.formatTime(texts.zone),
                missing,
            ),
        at = now,
        target = NotificationTarget.Mission(eventId, eventTitle, UserMode.ORGANIZER, mission.id),
    )
  }
}
