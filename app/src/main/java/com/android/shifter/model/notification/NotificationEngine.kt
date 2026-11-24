// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import com.swent.shifter.model.user.UserRepository
import com.swent.shifter.model.user.displayNames
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * How old an inbox notification may be and still be shown: an older one, found by a phone that was
 * off for days, tells something likely over, so it is removed without being shown.
 */
internal val MAX_INBOX_AGE: Duration = Duration.ofHours(48)

/**
 * The key of [InboxNotification.data] that names the sender, for the kinds whose named user is
 * always the one who acted. The engine writes there the name of the sender's public profile, since
 * the value in `data` is chosen by the writer and the rules only check
 * [InboxNotification.senderId].
 */
private val SENDER_NAME_KEYS =
    mapOf(
        NotificationKind.NEW_APPLICATION to NotificationData.Key.USER_NAME,
        NotificationKind.ANNOUNCEMENT to NotificationData.Key.SENDER_NAME,
    )

/**
 * Turns a user's inbox and the [TimedCheck]s into what the [Notifier] shows, skipping the kinds the
 * user turned off. The same engine runs with the app open, on each inbox change and each minute,
 * and with the app closed, every 15 minutes (#614).
 *
 * Every inbox notification it handles leaves the inbox: shown, turned off, too old or unreadable by
 * its format. One of a kind with no format in this version of the app stays, until it is older than
 * [MAX_INBOX_AGE]. While the [Notifier] cannot show anything, as without the permission, it
 * consumes nothing: neither the inbox nor the checks' [SeenStore]s change until it can.
 *
 * Its runs never overlap, even when the app and a background run call it at once, so two runs never
 * both add to the same notification and lose one's items: the app holds one engine per process.
 *
 * @param formats one per kind with [NotificationKind.inInbox] at most.
 * @param checks of kinds without [NotificationKind.inInbox] only.
 * @param checks of kinds without [NotificationKind.inInbox] only.
 * @param shownItems the [ShownItems] of a user.
 * @param seenStore the [SeenStore] of a user for one kind.
 */
class NotificationEngine(
    private val inbox: NotificationInbox,
    private val preferences: NotificationPreferences,
    private val users: UserRepository,
    formats: List<NotificationFormat>,
    private val checks: List<TimedCheck>,
    private val notifier: Notifier,
    private val shownItems: (userId: String) -> ShownItems,
    private val seenStore: (userId: String, kind: NotificationKind) -> SeenStore,
    private val texts: NotificationTexts,
) {
  private val formats: Map<NotificationKind, NotificationFormat> = formats.associateBy { it.kind }

  /** Held by each run, so they never overlap. */
  private val mutex = Mutex()

  init {
    require(this.formats.size == formats.size) { "One format per kind" }
    require(formats.all { it.kind.inInbox }) { "A format reads an inbox kind" }
    require(checks.none { it.kind.inInbox }) { "A check computes a kind outside the inbox" }
  }

  /**
   * Handles the inbox of [userId], then runs the checks, at [now], the checks even when the inbox
   * cannot be read, since they do not depend on it. Throws the first failure once both are done,
   * the inbox's first: the caller runs it again later. Nothing was removed from the inbox without
   * being handled, but a check's notification whose showing failed is lost, as the check already
   * marked it in its [SeenStore] (see [TimedCheck.run]); Android does not fail to show one.
   */
  suspend fun run(userId: String, now: Instant) {
    mutex.withLock {
      val inboxFailure = failureOf { inboxNow(userId, now) }
      val checksFailure = failureOf { checksNow(userId, now) }
      (inboxFailure ?: checksFailure)?.let { throw it }
    }
  }

  /**
   * Shows the pending inbox notifications of [userId] and removes the ones handled. It reads them
   * itself, once no other run can change them: a list read before, while another run was showing
   * and removing its items, could show them again. With the app open, a change of
   * [NotificationInbox.observePending] is only the signal to call it.
   *
   * It throws when the inbox or the preferences cannot be read, before removing anything: a kind
   * turned off must never be mistaken for one turned on. A kind whose format or showing fails keeps
   * its items in the inbox, until they are too old, without stopping the other kinds, and the first
   * failure is thrown once the others are removed: one malformed notification never blocks the
   * inbox. A removal that fails throws too; what was shown then is shown again, without counting an
   * item twice, at the next run.
   */
  suspend fun handleInbox(userId: String, now: Instant) = mutex.withLock { inboxNow(userId, now) }

  /**
   * Runs every check of a kind [userId] did not turn off, at [now]. A failing check does not stop
   * the others: the first failure is thrown once they all ran.
   */
  suspend fun runChecks(userId: String, now: Instant) = mutex.withLock { checksNow(userId, now) }

  /** [handleInbox], with the [mutex] held. */
  private suspend fun inboxNow(userId: String, now: Instant) {
    if (!notifier.canShow()) return
    val items = inbox.pending(userId)
    val disabled = preferences.disabledKinds(userId).first()
    val (wanted, unwanted) =
        items.partition { it.kind !in disabled && !it.createdAt.isBefore(now - MAX_INBOX_AGE) }
    val readable = wanted.filter { it.kind in formats }
    val shown = shownItems(userId).apply { retainOnly(notifier.activeIdentities()) }
    var failure: Exception? = null
    val failedKinds = mutableSetOf<NotificationKind>()
    withSenderNames(readable)
        .groupBy { it.kind }
        .forEach { (kind, kindItems) ->
          failureOf { show(formats.getValue(kind), kindItems, shown) }
              ?.let {
                failedKinds += kind
                failure = failure ?: it
              }
        }
    val handled = unwanted + readable.filter { it.kind !in failedKinds }
    if (handled.isNotEmpty()) inbox.remove(userId, handled.map { it.id })
    failure?.let { throw it }
  }

  /** [runChecks], with the [mutex] held. */
  private suspend fun checksNow(userId: String, now: Instant) {
    if (!notifier.canShow()) return
    val disabled = preferences.disabledKinds(userId).first()
    var failure: Exception? = null
    for (check in checks.filter { it.kind !in disabled }) {
      val checkFailure = failureOf {
        notifier.show(check.run(userId, now, seenStore(userId, check.kind)))
      }
      failure = failure ?: checkFailure
    }
    failure?.let { throw it }
  }

  /**
   * The exception [block] throws, or null when it returns. A cancellation is thrown on rather than
   * returned, so a cancelled run stops at once.
   */
  private inline fun failureOf(block: () -> Unit): Exception? =
      try {
        block()
        null
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        e
      }

  /**
   * [items] with the name of each sender's public profile under its kind's [SENDER_NAME_KEYS],
   * keeping the name in `data` when the profile cannot be read or has no name.
   */
  private suspend fun withSenderNames(items: List<InboxNotification>): List<InboxNotification> {
    val senders = items.filter { it.kind in SENDER_NAME_KEYS }.map { it.senderId }
    if (senders.isEmpty()) return items
    val names = users.displayNames(senders)
    return items.map { item ->
      val key = SENDER_NAME_KEYS[item.kind]
      val name = names[item.senderId]
      if (key == null || name == null) item else item.copy(data = item.data + (key to name))
    }
  }

  /**
   * Shows, for each identity [items] join, the notification [format] makes of the items already
   * shown under it and the new ones. The identity of an item is the one of the notifications the
   * format makes of it alone; an item it makes none of is not shown. A format must give a
   * notification the identity its items give alone, whatever the other items: one that gave another
   * would have its items removed without being shown.
   *
   * Each identity is formatted with its own items only, so a notification always shows what is
   * stored under its identity, even with a format making several notifications of one item, and a
   * notification no new item joins is not shown again.
   */
  private fun show(format: NotificationFormat, items: List<InboxNotification>, shown: ShownItems) {
    val newItems =
        items
            .flatMap { item -> format.format(listOf(item), texts).map { it.identity to item } }
            .groupBy({ it.first }, { it.second })
    val grouped = newItems.mapValues { (identity, new) -> (shown.items(identity) + new).merged() }
    notifier.show(
        grouped.flatMap { (identity, identityItems) ->
          format.format(identityItems, texts).filter { it.identity == identity }
        }
    )
    grouped.forEach { (identity, identityItems) -> shown.put(identity, identityItems) }
  }

  /**
   * These items oldest first, each once. An item can come twice when its removal failed: it is then
   * both still in the inbox and among the items already shown.
   */
  private fun List<InboxNotification>.merged(): List<InboxNotification> {
    val once = distinctBy { it.id }
    return once.sortedBy { it.createdAt }
  }
}
