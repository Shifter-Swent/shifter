// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import kotlinx.coroutines.flow.Flow

/**
 * The reading side of a user's inbox: what is waiting for them, until their phone has handled it.
 * Only its owner reads it. The writing side is no repository: each action adds its notification in
 * its own transaction or batch, so the two can never disagree.
 *
 * Every call throws when the inbox cannot be read or written, and [observePending] ends with that
 * error: whoever runs it retries later rather than losing the notifications.
 */
interface NotificationInbox {

  /** The notifications waiting for [userId], oldest first. */
  suspend fun pending(userId: String): List<InboxNotification>

  /**
   * [pending] as it changes, for as long as the app is open: it emits the current list at once,
   * then again each time a notification arrives or is removed.
   */
  fun observePending(userId: String): Flow<List<InboxNotification>>

  /**
   * Removes the notifications [ids] of [userId] once handled; an id already gone is ignored. It
   * returns once the removal is recorded on the phone, without waiting for the server, so it never
   * blocks offline: the removal reaches the server when the phone is back online.
   */
  suspend fun remove(userId: String, ids: Collection<String>)
}
