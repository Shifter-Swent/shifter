// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import android.content.Context
import java.time.ZoneId

/**
 * The [NotificationTexts] of the phone: the strings of `strings_notifications.xml` in its current
 * language, and its current time zone, both read at each call, so a notification made after the
 * user changed either follows it.
 */
class AndroidNotificationTexts(private val context: Context) : NotificationTexts {

  override val zone: ZoneId
    get() = ZoneId.systemDefault()

  override fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

  override fun plural(id: Int, count: Int, vararg args: Any): String =
      context.resources.getQuantityString(id, count, *args)
}
