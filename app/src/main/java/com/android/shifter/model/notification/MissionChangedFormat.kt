// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import com.swent.shifter.R
import com.swent.shifter.model.notification.NotificationData.Key
import com.swent.shifter.model.notification.NotificationData.Value
import com.swent.shifter.model.time.formatTime

/**
 * Shows the [NotificationKind.MISSION_CHANGED] of a volunteer: one notification per event, keyed by
 * the event. One mission changed reads "Serve at the bar now runs Sat 15:00–17:00.", "Serve at the
 * bar moved to Rue de Genève 12.", "Serve at the bar no longer has a place.", or a new time and a
 * place in one sentence; several read "3 of your missions changed.".
 *
 * A mission changed more than once is told once, as it is after the latest change, with every kind
 * of change it had: a new time, then a new place, reads as both. A tap opens the Planning on the
 * day of the mission, or of the first one when several changed.
 */
class MissionChangedFormat : NotificationFormat {

  override val kind = NotificationKind.MISSION_CHANGED

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
    val changes = mergedChanges(items)
    val first = changes.minByOrNull { it.line.startAt } ?: return null
    val eventTitle = items.last().eventTitle
    val text =
        if (changes.size == 1) first.text(texts)
        else texts.plural(R.plurals.notification_mission_changed_many, changes.size, changes.size)
    return AppNotification(
        kind = kind,
        key = eventId,
        title = eventTitle,
        text = text,
        at = items.maxOf { it.createdAt },
        target = planningOf(eventId, eventTitle, first.line, texts.zone),
    )
  }

  /** One [Change] per mission of [items], each merged with the later ones of that mission. */
  private fun mergedChanges(items: List<InboxNotification>): List<Change> {
    val byMission = LinkedHashMap<String, Change>()
    for (item in items) {
      val change = item.toChange() ?: continue
      byMission[change.line.id] = byMission[change.line.id]?.then(change) ?: change
    }
    return byMission.values.toList()
  }

  /**
   * What one document says changed, or null when it cannot be read: no readable mission, or an
   * unknown `what`. A new place without an address is one the mission no longer has.
   */
  private fun InboxNotification.toChange(): Change? {
    val line = MissionLine.decodeAll(data[Key.MISSIONS].orEmpty()).firstOrNull() ?: return null
    val address = data[Key.ADDRESS]
    return when (data[Key.WHAT]) {
      Value.TIME -> Change(line, timeChanged = true, placeChanged = false, address = null)
      Value.PLACE -> Change(line, timeChanged = false, placeChanged = true, address = address)
      Value.TIME_AND_PLACE ->
          Change(line, timeChanged = true, placeChanged = true, address = address)
      else -> null
    }
  }

  /**
   * A change of the mission [line], as it is once changed: its time, its place, or both, never
   * neither.
   *
   * @property address the new address when [placeChanged], or null when the mission no longer has a
   *   place.
   */
  private data class Change(
      val line: MissionLine,
      val timeChanged: Boolean,
      val placeChanged: Boolean,
      val address: String?,
  ) {

    /** This change followed by [next], of the same mission: its place is the latest one told. */
    fun then(next: Change) =
        Change(
            next.line,
            timeChanged || next.timeChanged,
            placeChanged || next.placeChanged,
            if (next.placeChanged) next.address else address,
        )

    fun text(texts: NotificationTexts): String {
      val day = line.startAt.formatDayOfWeek(texts.zone)
      val start = line.startAt.formatTime(texts.zone)
      val end = line.endAt.formatTime(texts.zone)
      return when {
        !placeChanged ->
            texts.string(R.string.notification_mission_changed_time, line.title, day, start, end)
        address == null && timeChanged ->
            texts.string(
                R.string.notification_mission_changed_time_and_no_place,
                line.title,
                day,
                start,
                end,
            )
        address == null -> texts.string(R.string.notification_mission_changed_no_place, line.title)
        timeChanged ->
            texts.string(
                R.string.notification_mission_changed_time_and_place,
                line.title,
                day,
                start,
                end,
                address,
            )
        else -> texts.string(R.string.notification_mission_changed_place, line.title, address)
      }
    }
  }
}
