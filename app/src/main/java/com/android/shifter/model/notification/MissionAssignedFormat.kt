// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import com.swent.shifter.R
import com.swent.shifter.model.notification.NotificationData.Key
import com.swent.shifter.model.notification.NotificationData.Value
import com.swent.shifter.model.time.formatTime

/**
 * Shows the [NotificationKind.MISSION_ASSIGNED] of a volunteer: one notification per event, keyed
 * by the event, so three missions in one document or three documents of one event give a single
 * notification: "You're on Bar, Sat 14:00.", "3 new missions at Summer Festival.", "You were
 * removed from Clean the park.", or an addition and a removal one after the other.
 *
 * For a mission listed more than once, the latest change counts, so one added then removed is told
 * as removed. A tap opens the Planning on the day of the first mission added, or removed when none
 * was added.
 */
class MissionAssignedFormat : NotificationFormat {

  override val kind = NotificationKind.MISSION_ASSIGNED

  override fun format(
      items: List<InboxNotification>,
      texts: NotificationTexts,
  ): List<AppNotification> =
      items
          .groupBy { it.eventId }
          .mapNotNull { (eventId, ofEvent) -> notify(eventId, ofEvent, texts) }

  /** The notification of the event [eventId], or null when none of its [items] can be read. */
  private fun notify(
      eventId: String,
      items: List<InboxNotification>,
      texts: NotificationTexts,
  ): AppNotification? {
    val latest = latestChanges(items)
    val added = latest.filterValues { it == Value.ADDED }.keys.sortedBy { it.startAt }
    val removed = latest.filterValues { it == Value.REMOVED }.keys.sortedBy { it.startAt }
    val first = added.firstOrNull() ?: removed.firstOrNull() ?: return null
    val eventTitle = items.last().eventTitle
    return AppNotification(
        kind = kind,
        key = eventId,
        title = eventTitle,
        text =
            listOfNotNull(addedText(added, eventTitle, texts), removedText(removed, texts))
                .joinToString(" "),
        at = items.maxOf { it.createdAt },
        target = planningOf(eventId, eventTitle, first, texts.zone),
    )
  }

  /**
   * The latest change, ADDED or REMOVED, of each mission of [items], oldest first, keyed by the
   * line of that change. An item with an unknown change or no readable mission is skipped.
   */
  private fun latestChanges(items: List<InboxNotification>): Map<MissionLine, String> {
    val byMission = LinkedHashMap<String, Pair<MissionLine, String>>()
    for (item in items) {
      val change = item.data[Key.CHANGE]?.takeIf { it == Value.ADDED || it == Value.REMOVED }
      if (change == null) continue
      MissionLine.decodeAll(item.data[Key.MISSIONS].orEmpty()).forEach { line ->
        byMission[line.id] = line to change
      }
    }
    return byMission.values.toMap()
  }

  private fun addedText(
      added: List<MissionLine>,
      eventTitle: String,
      texts: NotificationTexts,
  ): String? =
      when (added.size) {
        0 -> null
        1 -> {
          val line = added.single()
          texts.string(
              R.string.notification_mission_assigned_added_one,
              line.title,
              line.startAt.formatDayOfWeek(texts.zone),
              line.startAt.formatTime(texts.zone),
          )
        }
        else ->
            texts.plural(
                R.plurals.notification_mission_assigned_added_many,
                added.size,
                added.size,
                eventTitle,
            )
      }

  private fun removedText(removed: List<MissionLine>, texts: NotificationTexts): String? =
      when (removed.size) {
        0 -> null
        1 ->
            texts.string(R.string.notification_mission_assigned_removed_one, removed.single().title)
        else ->
            texts.plural(
                R.plurals.notification_mission_assigned_removed_many,
                removed.size,
                removed.size,
            )
      }
}
