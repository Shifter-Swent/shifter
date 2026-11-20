// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

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
   */
  fun format(items: List<InboxNotification>, texts: NotificationTexts): List<AppNotification>
}

/**
 * The texts of `strings_notifications.xml`, read in the language of the phone. A test passes one
 * that returns the resource name and its arguments.
 */
interface NotificationTexts {

  /** The string [id], formatted with [args]. */
  fun string(@StringRes id: Int, vararg args: Any): String

  /** The plural [id] for [count], formatted with [args]. */
  fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String
}
