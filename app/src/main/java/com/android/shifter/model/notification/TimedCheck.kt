// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import java.time.Instant

/**
 * A notification nobody writes, computed on the phone from the current time: a missed check-in, or
 * open spots before a mission. It reads what it needs from the repositories it is built with.
 */
interface TimedCheck {

  /** The kind it shows, one without [NotificationKind.inInbox]. */
  val kind: NotificationKind

  /**
   * What to show to [userId] at [now]. It skips the keys [seen] already shown and marks the ones it
   * returns, so a check that runs every minute shows each notification once, and forgets a key
   * whose situation is over, so it can be shown again if it comes back. It may ask a
   * [NotificationScheduler] to run again when its next notification is due.
   */
  suspend fun run(userId: String, now: Instant, seen: SeenStore): List<AppNotification>
}

/**
 * The [AppNotification.key]s a [TimedCheck] already showed, kept on the phone for one user and one
 * [TimedCheck.kind], since a key is only unique within its kind.
 */
interface SeenStore {

  /** Whether [key] was shown and not forgotten since. */
  fun wasShown(key: String): Boolean

  /** Remembers that [key] was shown. */
  fun markShown(key: String)

  /**
   * Forgets [key], once what it is about is over: a mission full again, or ended. A key whose
   * mission vanished before that stays, which only costs its room.
   */
  fun forget(key: String)
}

/** Runs the notification checks again at a given time, even if the app is closed by then. */
fun interface NotificationScheduler {

  /**
   * Checks the notifications again at [time], or as soon as possible if it has passed. Each
   * distinct time gets its own run, so asking for a later one never drops an earlier one; asking
   * again for a time already scheduled adds none, so a check may ask on each of its runs. One run
   * handles everything due by then, whoever asked for it.
   */
  fun checkAt(time: Instant)
}
