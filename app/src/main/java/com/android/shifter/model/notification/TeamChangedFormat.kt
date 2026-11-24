// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import com.swent.shifter.R
import com.swent.shifter.model.time.formatTime
import com.swent.shifter.model.user.UserMode

/**
 * The format of [NotificationKind.TEAM_CHANGED]: tells a manager who joined or left their team,
 * "Alice Martin joined Bar." or "Léa Petit left Bar — was on: Serve at the bar 12:00, Bar · night
 * service 20:00.". A tap opens the event's Overview on the team's card, on the volunteer side,
 * which is where a manager runs their team.
 *
 * One notification per change, keyed by the document that carries it: the design shows a join and a
 * departure of the same team side by side, so a manager sees every change rather than only the
 * last. Nothing is shown for a document that says neither which team changed nor how, which is
 * removed from the inbox all the same.
 */
class TeamChangedFormat : NotificationFormat {

  override val kind = NotificationKind.TEAM_CHANGED

  override fun format(
      items: List<InboxNotification>,
      texts: NotificationTexts,
  ): List<AppNotification> = items.mapNotNull { notification(it, texts) }

  /** What [item] shows, or null when it says neither which team changed nor how. */
  private fun notification(item: InboxNotification, texts: NotificationTexts): AppNotification? {
    val teamName =
        item.data[NotificationData.Key.TEAM_NAME]?.takeIf { it.isNotBlank() } ?: return null
    // A writer that could not read the name still tells the manager that their team changed.
    val userName =
        item.data[NotificationData.Key.USER_NAME]?.takeIf { it.isNotBlank() }
            ?: texts.string(R.string.notification_team_changed_someone)
    val text =
        when (item.data[NotificationData.Key.CHANGE]) {
          NotificationData.Value.JOINED ->
              texts.string(R.string.notification_team_changed_joined, userName, teamName)
          NotificationData.Value.LEFT -> leftText(item, texts, userName, teamName)
          // A change a newer version of the app writes and this one cannot name.
          else -> return null
        }
    return AppNotification(
        kind = kind,
        key = item.id,
        title = item.eventTitle,
        text = text,
        at = item.createdAt,
        // Without a team the Overview still opens, on no card rather than on the wrong one.
        target =
            NotificationTarget.EventOverview(
                item.eventId,
                item.eventTitle,
                UserMode.VOLUNTEER,
                item.data[NotificationData.Key.TEAM_ID],
            ),
    )
  }

  /**
   * "Léa Petit left Bar", with the missions the departure took her off when it took her off any:
   * the lines the writer stored, each shown with the title and the start it had then, since the
   * mission may have changed again or be gone. A malformed line is skipped by
   * [MissionLine.decodeAll], and a departure left with none reads as one from no mission.
   */
  private fun leftText(
      item: InboxNotification,
      texts: NotificationTexts,
      userName: String,
      teamName: String,
  ): String {
    val missions = MissionLine.decodeAll(item.data[NotificationData.Key.MISSIONS].orEmpty())
    if (missions.isEmpty()) {
      return texts.string(R.string.notification_team_changed_left, userName, teamName)
    }
    val separator = texts.string(R.string.notification_team_changed_mission_separator)
    val wasOn =
        missions.joinToString(separator) {
          texts.string(
              R.string.notification_team_changed_mission,
              it.title,
              it.startAt.formatTime(texts.zone),
          )
        }
    return texts.string(
        R.string.notification_team_changed_left_on_missions,
        userName,
        teamName,
        wasOn,
    )
  }
}
