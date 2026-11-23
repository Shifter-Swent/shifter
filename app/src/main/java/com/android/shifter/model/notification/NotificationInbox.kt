// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import kotlinx.coroutines.flow.Flow

/**
 * The reading side of a user's inbox: what is waiting for them, until their phone has handled it.
 * Only its owner reads it. The writing side is no repository: each action adds its notification in
 * its own transaction or batch, so the two can never disagree.
 *
 * [pending] throws a [NotificationInboxException] when the inbox cannot be read, and
 * [observePending] ends with that error: whoever runs it retries later rather than losing the
 * notifications. [remove] only throws when it cannot even record the removal, see there.
 */
interface NotificationInbox {

  /**
   * The notifications waiting for [userId], oldest first. One this app cannot read, of a kind it
   * does not know or malformed, is left out rather than failing the others.
   */
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
   *
   * It throws a [NotificationInboxException] only when the removal cannot be recorded at all, and
   * then removes nothing. A removal the backend refuses afterwards, such as one in someone else's
   * inbox, is not reported: the notification comes back in [pending] and [observePending].
   */
  suspend fun remove(userId: String, ids: Collection<String>)
}
