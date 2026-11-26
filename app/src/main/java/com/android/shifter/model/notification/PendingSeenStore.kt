// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

/**
 * The [SeenStore] the [NotificationEngine] runs a check with: it holds the check's changes until
 * [commit], so a key the check marks reaches [stored] only once its notification is shown, and one
 * whose showing failed is shown again at the next run. A key the check forgets reaches [stored]
 * whether its notifications were shown or not, as forgetting early can at worst show a notification
 * twice. A run cancelled in the check commits nothing, and the next run does it again.
 *
 * The check sees its own changes in [wasShown] while it runs.
 */
internal class PendingSeenStore(private val stored: SeenStore) : SeenStore {

  private val forgotten = mutableSetOf<String>()

  private val marked = mutableSetOf<String>()

  override fun wasShown(key: String) = key in marked || (key !in forgotten && stored.wasShown(key))

  override fun markShown(key: String) {
    marked += key
  }

  /** Forgets [key], and a mark of [key] earlier in the same run. */
  override fun forget(key: String) {
    forgotten += key
    marked -= key
  }

  /**
   * Writes the forgotten keys in [stored], then the marked ones when they were [shown]. The keys
   * forgotten then marked again are forgotten in any case, so a failed showing never leaves the
   * earlier mark of a key whose notification was not shown.
   */
  fun commit(shown: Boolean) {
    forgotten.forEach(stored::forget)
    if (shown) marked.forEach(stored::markShown)
  }
}
