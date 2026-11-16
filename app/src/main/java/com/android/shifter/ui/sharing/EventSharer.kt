// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.sharing

import android.content.Context
import android.content.Intent
import com.swent.shifter.R

/**
 * Hands an event's join code to another app, so the organizer can send it to volunteers. Behind an
 * interface so tests check what is shared without opening the phone's share sheet.
 */
fun interface EventSharer {
  /** Offers to send the [joinCode] of the event [eventTitle]. */
  fun share(eventTitle: String, joinCode: String)
}

/** Opens the phone's share sheet with a message holding the event's name and its code. */
class AndroidEventSharer(private val context: Context) : EventSharer {
  override fun share(eventTitle: String, joinCode: String) {
    val send =
        Intent(Intent.ACTION_SEND).apply {
          type = "text/plain"
          putExtra(
              Intent.EXTRA_TEXT,
              context.getString(R.string.sharing_message, eventTitle, joinCode),
          )
        }
    // The context may be the application's rather than an activity, which needs a new task.
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }
}
