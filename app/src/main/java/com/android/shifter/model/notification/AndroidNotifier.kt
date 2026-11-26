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

  /** False without `POST_NOTIFICATIONS` on Android 13+, or with every notification turned off. */
  override fun canShow() = manager.areNotificationsEnabled()

  // Checked by canShow, which the engine calls before showing anything: without the permission,
  // Android drops the notification without a word rather than throwing.
  @SuppressLint("MissingPermission")
  override fun show(notifications: List<AppNotification>) {
    notifications.forEach { manager.notify(it.identity, NOTIFICATION_ID, build(it)) }
    notifications
        .mapNotNull { notification -> notification.target.event()?.let { it to notification } }
        .associate { it }
        .forEach { (event, last) -> manager.notify(event.id, SUMMARY_ID, summary(event, last)) }
  }

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
          .setShowWhen(true)
          .setContentIntent(tapPendingIntent(notification))
          .setAutoCancel(true)
          .apply { notification.target.event()?.let { setGroup(it.id) } }
          .build()

  /**
   * The summary of [event]'s group, which Android shows in place of its notifications once they
   * fold, and on its own before Android 7. Silent, so a notification sounds once, not twice. On the
   * channel of [last], the latest shown in the group: a summary needs one, and that one is on.
   */
  private fun summary(event: EventRef, last: AppNotification) =
      NotificationCompat.Builder(context, last.kind.channelId)
          .setSmallIcon(R.drawable.ic_notification)
          .setContentTitle(event.title)
          .setContentText(last.text)
          .setGroup(event.id)
          .setGroupSummary(true)
          .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
          .setAutoCancel(true)
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
