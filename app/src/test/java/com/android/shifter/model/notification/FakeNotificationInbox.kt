// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [NotificationInbox] for unit tests, with one inbox per user. Its flows are live: a
 * notification delivered or removed while a test collects [observePending] is emitted right away.
 *
 * It also stands for the writing side: the fake repository of an action calls [deliver] where the
 * Firestore one adds the document to its batch, and [sent] keeps everything delivered, removed or
 * not, so a test checks that the action notified the right person. Like the rules, [deliver]
 * refuses what Firestore would refuse, since that refusal would fail the whole action.
 *
 * @param clock gives [InboxNotification.createdAt], as the server's clock would.
 */
class FakeNotificationInbox(
    failure: Exception? = null,
    private val clock: () -> Instant = { Instant.EPOCH },
) : NotificationInbox {

  /** The inboxes and the failure, in one flow, so a failure set while collected ends the flow. */
  private val state = MutableStateFlow(State(emptyMap(), failure))

  private val delivered = mutableListOf<Sent>()

  private var nextId = 1

  /** A notification delivered to [recipientId]. */
  data class Sent(val recipientId: String, val notification: InboxNotification)

  /** Everything [deliver]ed so far, in order, including what was removed since. */
  val sent: List<Sent>
    get() = delivered.toList()

  /**
   * When set, every reading or removing call and every new collection throws it, to simulate a
   * backend error; [deliver] stands for the writer's batch and ignores it. Setting it while
   * [observePending] is collected ends that flow with it too, like a failing listener.
   */
  var failure: Exception?
    get() = state.value.failure
    set(value) {
      state.update { it.copy(failure = value) }
    }

  /**
   * Puts a notification in the inbox of [recipientId], as a writer's batch would, with a new id and
   * the [clock]'s time, and returns it. Throws [IllegalArgumentException] for what the rules
   * refuse: a notification to its own sender, a kind computed on the phone, or a key of [data]
   * outside [NotificationData.Key.ALL].
   */
  fun deliver(
      recipientId: String,
      kind: NotificationKind,
      eventId: String,
      eventTitle: String,
      senderId: String,
      data: Map<String, String>,
  ): InboxNotification {
    require(senderId != recipientId) { "Nobody writes in their own inbox" }
    val notification =
        InboxNotification(
            "notification-${nextId++}",
            kind,
            eventId,
            eventTitle,
            senderId,
            data,
            clock(),
        )
    delivered += Sent(recipientId, notification)
    state.update {
      it.copy(inboxes = it.inboxes + (recipientId to it.of(recipientId) + notification))
    }
    return notification
  }

  override suspend fun pending(userId: String): List<InboxNotification> =
      state.value.pendingOf(userId)

  override fun observePending(userId: String): Flow<List<InboxNotification>> =
      state.map { it.pendingOf(userId) }.distinctUntilChanged()

  override suspend fun remove(userId: String, ids: Collection<String>) {
    state.value.failure?.let { throw it }
    state.update { s ->
      s.copy(inboxes = s.inboxes + (userId to s.of(userId).filterNot { it.id in ids }))
    }
  }

  private data class State(
      val inboxes: Map<String, List<InboxNotification>>,
      val failure: Exception?,
  ) {
    fun of(userId: String) = inboxes[userId].orEmpty()

    fun pendingOf(userId: String): List<InboxNotification> {
      failure?.let { throw it }
      return of(userId).sortedBy { it.createdAt }
    }
  }
}

/**
 * An inbox notification for the test of a [NotificationFormat], with placeholder values for what
 * the test does not check. Pass a different [id] to each item of one call.
 */
fun inboxNotification(
    id: String = "notification-1",
    kind: NotificationKind = NotificationKind.NEW_APPLICATION,
    eventId: String = "event-1",
    eventTitle: String = "Event",
    senderId: String = "sender-1",
    data: Map<String, String> = emptyMap(),
    createdAt: Instant = Instant.EPOCH,
) = InboxNotification(id, kind, eventId, eventTitle, senderId, data, createdAt)

/** A [SeenStore] in memory, empty at first. */
class InMemorySeenStore : SeenStore {
  private val shown = mutableSetOf<String>()

  override fun wasShown(key: String) = key in shown

  override fun markShown(key: String) {
    shown += key
  }

  override fun forget(key: String) {
    shown -= key
  }
}

/**
 * [NotificationPreferences] in memory, with every kind on for everyone at first, as in the app. A
 * test turns kinds off for a user with [disable], live while the flow is collected.
 */
class FakeNotificationPreferences : NotificationPreferences {

  /**
   * The kinds turned off and the failure, in one flow, so a failure set while collected ends it.
   */
  private val state = MutableStateFlow(State(emptyMap(), null))

  /**
   * When set, a new collection of [disabledKinds] throws it, and one running ends with it, like a
   * profile that cannot be read: the caller must not take it for "all on".
   */
  var failure: Exception?
    get() = state.value.failure
    set(value) {
      state.update { it.copy(failure = value) }
    }

  /** Turns [kinds] off for [userId], as Settings would. */
  fun disable(userId: String, vararg kinds: NotificationKind) {
    state.update { it.copy(disabled = it.disabled + (userId to it.of(userId) + kinds)) }
  }

  override fun disabledKinds(userId: String): Flow<Set<NotificationKind>> =
      state
          .map {
            it.failure?.let { failure -> throw failure }
            it.of(userId)
          }
          .distinctUntilChanged()

  private data class State(
      val disabled: Map<String, Set<NotificationKind>>,
      val failure: Exception?,
  ) {
    fun of(userId: String) = disabled[userId].orEmpty()
  }
}

/** A [NotificationScheduler] that records the times it was asked to check at, in order. */
class FakeNotificationScheduler : NotificationScheduler {
  val requested = mutableListOf<Instant>()

  override fun checkAt(time: Instant) {
    requested += time
  }
}

/**
 * [NotificationTexts] that write the resource id and the arguments, for tests without Android, in
 * the [zone] the test chooses, such as `ZoneOffset.UTC`.
 */
class FakeNotificationTexts(override val zone: ZoneId) : NotificationTexts {
  override fun string(id: Int, vararg args: Any) = "$id${args.toList()}"

  override fun plural(id: Int, count: Int, vararg args: Any) = "$id#$count${args.toList()}"
}
