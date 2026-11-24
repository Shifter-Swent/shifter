// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import com.swent.shifter.model.chat.ChannelKind
import com.swent.shifter.model.user.UserMode
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.Serializable

/**
 * A notification as it is shown, made by a [NotificationFormat] or a [TimedCheck].
 *
 * @property key identifies what it is about, within its [kind]: a notification with the kind and
 *   the key of one still shown replaces it rather than adding a second one, while two kinds never
 *   replace each other, even with the same key, such as a mission's id. A new item joins the
 *   notification it replaces, as the engine gives the format the items of that notification again
 *   (see [NotificationFormat.format]): a key per event makes "3 new applications", with the app
 *   open or closed.
 * @property at when what it tells happened, the inbox document's [InboxNotification.createdAt] or
 *   the time a check found it, shown rather than the time it is displayed, which may come up to 15
 *   minutes later with the app closed.
 * @property target the screen a tap opens.
 */
data class AppNotification(
    val kind: NotificationKind,
    val key: String,
    val title: String,
    val text: String,
    val at: Instant,
    val target: NotificationTarget,
) {
  /**
   * What identifies it on the phone, its [kind] and its [key]: showing one with the identity of a
   * notification still shown replaces it, and two kinds never share one.
   */
  val identity: String
    get() = "${kind.name}:$key"
}

/**
 * The screen a tap on an [AppNotification] opens, even with the app closed. It holds what the
 * routes need, the event's title included, so nothing is read before opening it, and it is
 * [Serializable], like the routes, so it travels in the notification's intent.
 *
 * [mode] is the side of the app to open, for the screens that exist on both: a manager is a
 * volunteer, so [UserMode.VOLUNTEER] opens their Overview too.
 */
@Serializable
sealed interface NotificationTarget {

  /** The event's Overview, on the card of the team [teamId] when there is one. */
  @Serializable
  data class EventOverview(
      val eventId: String,
      val eventTitle: String,
      val mode: UserMode,
      val teamId: String?,
  ) : NotificationTarget

  /**
   * The volunteer's Planning of the event, on [day]: the event day of the mission's start,
   * `startAt.eventDay(zone)` (`ui/components/days/GroupByDay.kt`), not its calendar date, since the
   * Planning lists a day from 04:00 to 04:00 (#558) and a mission at 02:00 is under the day before.
   *
   * Written by `LocalDate.toString()`, such as `2026-11-27`: a target that is not a date is refused
   * when built, and when read back from an intent, rather than failing on the tap.
   */
  @Serializable
  data class Planning(val eventId: String, val eventTitle: String, val day: String) :
      NotificationTarget {
    init {
      LocalDate.parse(day)
    }
  }

  /** The mission [missionId], in the Overview of its manager or of the organizer. */
  @Serializable
  data class Mission(
      val eventId: String,
      val eventTitle: String,
      val mode: UserMode,
      val missionId: String,
  ) : NotificationTarget

  /** A channel of the event, named as in the Chat routes: [teamId] for a team's only. */
  @Serializable
  data class Channel(
      val eventId: String,
      val eventTitle: String,
      val mode: UserMode,
      val kind: ChannelKind,
      val teamId: String?,
  ) : NotificationTarget {
    init {
      require((kind == ChannelKind.TEAM) == (teamId != null)) {
        "A team's channel has a teamId, and only a team's"
      }
    }
  }

  /** The organizer's membership requests of the event. */
  @Serializable
  data class Requests(val eventId: String, val eventTitle: String) : NotificationTarget

  @Serializable data class MyEvents(val mode: UserMode) : NotificationTarget
}
