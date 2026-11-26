// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

/**
 * Where the [NotificationEngine] shows what it made: the phone's notifications on Android, a list
 * in a test. Free of Android types, so the engine is tested without it.
 */
interface Notifier {

  /**
   * Shows [notifications], each replacing the one still shown with the same
   * [AppNotification.identity], if any. Nothing happens for an empty list.
   */
  fun show(notifications: List<AppNotification>)

  /**
   * The [AppNotification.identity] of each notification still shown: one the user swiped away or
   * tapped is no longer in it.
   */
  fun activeIdentities(): Set<String>

  /**
   * Whether what [show] shows reaches the user: false without the permission to notify, when
   * Android drops every notification without a word, so the engine consumes nothing until then.
   */
  fun canShow(): Boolean

  /**
   * Removes every notification shown, once nobody is signed in: a tap must never open the screens
   * of an account signed out.
   */
  fun clear()
}

/**
 * The inbox notifications behind each notification still shown, for one user, kept on the phone so
 * a closed app still finds them. The engine gives them back to the [NotificationFormat] with the
 * new items of the same [AppNotification.identity], so "2 new applications" becomes "3", not "1".
 */
interface ShownItems {

  /** The items shown under [identity], oldest first, or none. */
  fun items(identity: String): List<InboxNotification>

  /** Replaces the items shown under [identity] with [items]. */
  fun put(identity: String, items: List<InboxNotification>)

  /**
   * Forgets the items of every identity outside [identities], the notifications no longer shown: a
   * new item for one of them then starts a new notification rather than adding to a dismissed one.
   */
  fun retainOnly(identities: Set<String>)
}
