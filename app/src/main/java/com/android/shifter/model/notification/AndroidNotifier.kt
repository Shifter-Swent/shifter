// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.swent.shifter.R

/**
 * The Android id of every notification: each is told apart by its tag, its
 * [AppNotification.identity], so two identities never share one, as two hash codes could.
 */
internal const val NOTIFICATION_ID = 0

/** The Android id of a group's summary, tagged with its event's id. */
internal const val SUMMARY_ID = 1

/**
 * The [Notifier] of the phone: one channel per [NotificationKind], named as its switch in Settings,
 * and one group per event, so the notifications of an event fold under its title.
 *
 * The channels are created, or renamed in the phone's current language, when it is built: Android
 * keeps what the user changed in them, such as the sound or turning one off.
 */
class AndroidNotifier(private val context: Context) : Notifier {

  private val manager = NotificationManagerCompat.from(context)

  /** The events the previous call of [show] posted into: see [summariesKept]. */
  private var previouslyPostedInto: Set<String> = emptySet()

  init {
    manager.createNotificationChannelsCompat(
        NotificationKind.entries.map { kind ->
          NotificationChannelCompat.Builder(
                  kind.channelId,
                  NotificationManagerCompat.IMPORTANCE_DEFAULT,
              )
              .setName(context.getString(kind.channelName))
              .setDescription(
                  context.getString(kind.channelDescription, *kind.channelDescriptionArgs)
              )
              .build()
        }
    )
  }

  /**
   * False without `POST_NOTIFICATIONS` on Android 13+, or with every notification turned off. True
   * with only some channels turned off in the phone's settings: Android drops what [show] posts on
   * them, and the engine consumes it as shown, as for a kind turned off in Settings.
   */
  override fun canShow() = manager.areNotificationsEnabled()

  // Checked by canShow, which the engine calls before showing anything: without the permission,
  // Android drops the notification without a word rather than throwing.
  @SuppressLint("MissingPermission")
  override fun show(notifications: List<AppNotification>) {
    if (notifications.isEmpty()) return
    val summaries = summaryChannels(notifications)
    val postedInto = summaries.keys.map { it.id }.toSet()
    val summarized = summariesKept(postedInto)
    notifications.forEach { manager.notify(it.identity, NOTIFICATION_ID, build(it)) }
    summaries
        .filterKeys { it.id !in summarized }
        .forEach { (event, channelId) ->
          manager.notify(event.id, SUMMARY_ID, summary(event, channelId))
        }
    previouslyPostedInto = postedInto
  }

  /**
   * The events whose summary is shown, once the summaries left without a notification are removed:
   * one the user dismissed every notification of would otherwise stay alone, and a tap on it does
   * nothing. Removing a summary removes its group's notifications too.
   *
   * Read before posting, and never counting as alone the group of an event [postedInto] by this
   * call or the previous one: `notify` only queues a notification, so the one just posted may not
   * be listed yet, and removing its summary would remove it.
   */
  private fun summariesKept(postedInto: Set<String>): Set<String> {
    val active = manager.activeNotifications
    val groups =
        active
            .filter { it.id == NOTIFICATION_ID }
            .mapNotNullTo(mutableSetOf()) { it.notification.group } +
            postedInto +
            previouslyPostedInto
    val (kept, alone) =
        active.filter { it.id == SUMMARY_ID && it.tag != null }.partition { it.tag in groups }
    alone.forEach { manager.cancel(it.tag, SUMMARY_ID) }
    return kept.mapTo(mutableSetOf()) { it.tag }
  }

  /**
   * The channel of the summary of each event of [notifications]: the channel of its latest
   * notification among those on a channel the user did not turn off, since Android drops a summary
   * on a channel turned off. An event whose notifications are all on channels turned off has none,
   * and its notifications are not shown either.
   */
  private fun summaryChannels(notifications: List<AppNotification>): Map<EventRef, String> =
      notifications
          .filter { isOn(it.kind.channelId) }
          .mapNotNull { notification ->
            notification.target.event()?.let { it to notification.kind.channelId }
          }
          .toMap()

  /** Whether the user left the channel [channelId] on in the phone's settings. */
  private fun isOn(channelId: String) =
      manager.getNotificationChannelCompat(channelId)?.importance !=
          NotificationManagerCompat.IMPORTANCE_NONE

  override fun activeIdentities(): Set<String> =
      manager.activeNotifications
          .filter { it.id == NOTIFICATION_ID && it.tag != null }
          .mapTo(mutableSetOf()) { it.tag }

  private fun build(notification: AppNotification) =
      NotificationCompat.Builder(context, notification.kind.channelId)
          .setSmallIcon(R.drawable.ic_notification)
          .setContentTitle(notification.title)
          .setContentText(notification.text)
          .setStyle(NotificationCompat.BigTextStyle().bigText(notification.text))
          .setWhen(notification.at.toEpochMilli())
          .setContentIntent(tapPendingIntent(notification))
          .setAutoCancel(true)
          .apply { notification.target.event()?.let { setGroup(it.id) } }
          .build()

  /**
   * The summary of [event]'s group, which Android shows as the header of its notifications once
   * they fold. Silent, so a notification sounds once, not twice. Posted once, when the group
   * starts: Android drops the updates of a notification beyond a few per second, and the engine
   * shows each kind in its own call, so posting it at each call could cost the update of a
   * notification.
   */
  private fun summary(event: EventRef, channelId: String) =
      NotificationCompat.Builder(context, channelId)
          .setSmallIcon(R.drawable.ic_notification)
          .setContentTitle(event.title)
          .setGroup(event.id)
          .setGroupSummary(true)
          .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
          .build()

  /**
   * The PendingIntent a tap on [notification] sends. Its intent's data, the notification's
   * identity, tells the notifications apart (see [notificationTapIntent]), hence the request code 0
   * for all; `FLAG_UPDATE_CURRENT` gives a notification replaced under its identity its new target.
   */
  private fun tapPendingIntent(notification: AppNotification): PendingIntent =
      PendingIntent.getActivity(
          context,
          0,
          notificationTapIntent(context, notification),
          PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )
}

/** The id of this kind's Android channel. Never change it: a user's settings are kept under it. */
internal val NotificationKind.channelId: String
  get() = "notification_${name.lowercase()}"

/** The event a notification is grouped under. */
private data class EventRef(val id: String, val title: String)

/** The event of this target, or null for [NotificationTarget.MyEvents], which has none. */
private fun NotificationTarget.event(): EventRef? =
    when (this) {
      is NotificationTarget.EventOverview -> EventRef(eventId, eventTitle)
      is NotificationTarget.Planning -> EventRef(eventId, eventTitle)
      is NotificationTarget.Mission -> EventRef(eventId, eventTitle)
      is NotificationTarget.Channel -> EventRef(eventId, eventTitle)
      is NotificationTarget.Requests -> EventRef(eventId, eventTitle)
      is NotificationTarget.MyEvents -> null
    }
