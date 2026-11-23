// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.swent.shifter.MainActivity
import java.time.DateTimeException
import kotlinx.serialization.json.Json

/** The extra of an intent holding its [NotificationTarget], written as JSON. */
const val NOTIFICATION_TARGET_EXTRA = "com.swent.shifter.notificationTarget"

/**
 * The intent a tap on [notification] sends: it opens [MainActivity], or brings it back when it is
 * already shown (it is `singleTop`), which then opens the notification's target.
 *
 * Its data is the notification's [AppNotification.identity], as
 * `shifter://notification/<identity>`, so the intents of two notifications always differ for
 * Android, which tells intents apart by their data but not their extras: the notifier can wrap each
 * in a `PendingIntent` with the same request code without the taps sharing one, which would open
 * the same target for all.
 */
fun notificationTapIntent(context: Context, notification: AppNotification): Intent =
    Intent(context, MainActivity::class.java)
        .setData(
            Uri.Builder()
                .scheme(NOTIFICATION_SCHEME)
                .authority(NOTIFICATION_AUTHORITY)
                .appendPath(notification.identity)
                .build()
        )
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putNotificationTarget(notification.target)

/** The scheme and authority of the data of [notificationTapIntent]. */
private const val NOTIFICATION_SCHEME = "shifter"
private const val NOTIFICATION_AUTHORITY = "notification"

/** Writes [target] in this intent, read back by [notificationTarget]. */
fun Intent.putNotificationTarget(target: NotificationTarget): Intent =
    putExtra(
        NOTIFICATION_TARGET_EXTRA,
        Json.encodeToString(NotificationTarget.serializer(), target),
    )

/**
 * The target written in this intent by [putNotificationTarget], or null when there is none, as when
 * the app is opened from its icon, or when it cannot be read, such as one written by an older
 * version of the app or a `Planning` whose day is not a date: the app then opens as usual rather
 * than failing on the tap.
 */
fun Intent.notificationTarget(): NotificationTarget? {
  val written = getStringExtra(NOTIFICATION_TARGET_EXTRA) ?: return null
  return try {
    Json.decodeFromString(NotificationTarget.serializer(), written)
  } catch (_: IllegalArgumentException) {
    // A SerializationException, or the require of a Channel's init.
    null
  } catch (_: DateTimeException) {
    // The init of a Planning, whose day is not a date.
    null
  }
}
