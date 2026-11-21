// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import java.time.ZoneId

/**
 * Turns the inbox notifications of one [kind] into what is shown. One per kind with
 * [NotificationKind.inInbox], each written by the task that writes that kind, so none of them waits
 * for another.
 *
 * Pure Kotlin: the texts come through [NotificationTexts], so a format is tested without Android.
 */
interface NotificationFormat {

  /** The kind this format reads. */
  val kind: NotificationKind

  /**
   * What to show for [items], all of [kind], oldest first. A format may group several into one ("3
   * new applications"), and may show nothing for an item it cannot read: that item is still removed
   * from the inbox.
   *
   * [items] holds the new items and, with them, the items of this kind's notifications still shown
   * on the phone, which the engine (#614) keeps. So a new item that replaces a notification, by its
   * [AppNotification.identity], is grouped with what that one showed rather than hiding it, the
   * same with the app open, where documents arrive one at a time, as with the app closed.
   */
  fun format(items: List<InboxNotification>, texts: NotificationTexts): List<AppNotification>
}

/**
 * The texts of `strings_notifications.xml`, read in the language of the phone. A test passes one
 * that returns the resource name and its arguments.
 */
interface NotificationTexts {

  /**
   * The time zone a format writes times and days in: the phone's, as on the screens. Given rather
   * than read by each format, so a test fixes it and passes the same on any machine.
   */
  val zone: ZoneId

  /** The string [id], formatted with [args]. */
  fun string(@StringRes id: Int, vararg args: Any): String

  /** The plural [id] for [count], formatted with [args]. */
  fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String
}
