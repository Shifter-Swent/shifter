// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import kotlinx.coroutines.flow.Flow

/**
 * The kinds of notifications a user turned off in Settings, read before showing one: a kind turned
 * off is never shown, and its inbox notifications are removed without a word. Every kind is on
 * until turned off.
 *
 * Read only: the choices live on the user's profile, written by Settings through `UserRepository`
 * (#619, #620), so they follow the user to another phone.
 */
fun interface NotificationPreferences {

  /**
   * The kinds [userId] turned off, emitted once their profile is read, with no value before, and
   * again on each change. A failure to read ends the flow with it, so the caller does not mistake
   * it for "all on".
   */
  fun disabledKinds(userId: String): Flow<Set<NotificationKind>>
}
