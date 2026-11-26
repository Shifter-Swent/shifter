// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import com.swent.shifter.model.user.FakeUserRepository
import com.swent.shifter.model.user.User
import com.swent.shifter.model.user.UserMode
import com.swent.shifter.model.user.UserRepositoryException
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [NotificationEngine]: each kind through its format, a new item joining the notification
 * still shown, what is removed without being shown (turned off, too old, unreadable), the sender's
 * name read from their profile, what stays in the inbox, the failures that remove nothing, nothing
 * consumed while nothing can be shown, the checks, and runs that never overlap.
 */
class NotificationEngineTest {

  private val user = "user-1"

  private val now = Instant.parse("2026-11-24T12:00:00Z")

  /** The time the next delivered notification is stored at. */
  private var deliveredAt = now

  private val inbox = FakeNotificationInbox(clock = { deliveredAt })

  private val preferences = FakeNotificationPreferences()

  private val users = FakeUserRepository(listOf(User("sender-1", "Alice", "alice@example.com")))

  private val notifier = FakeNotifier()

  private val shownItems = InMemoryShownItems()

  private val seenStores = mutableMapOf<NotificationKind, InMemorySeenStore>()

  private val applications = GroupingFormat(NotificationKind.NEW_APPLICATION)

  private val announcements = GroupingFormat(NotificationKind.ANNOUNCEMENT)

  private fun engine(
      formats: List<NotificationFormat> = listOf(applications, announcements),
      checks: List<TimedCheck> = emptyList(),
      preferences: NotificationPreferences = this.preferences,
      inbox: NotificationInbox = this.inbox,
  ) =
      NotificationEngine(
          inbox,
          preferences,
          users,
          formats,
          checks,
          notifier,
          { userId ->
            assertEquals(user, userId)
            shownItems
          },
          { userId, kind ->
            assertEquals(user, userId)
            seenStores.getOrPut(kind) { InMemorySeenStore() }
          },
          FakeNotificationTexts(ZoneOffset.UTC),
      )

  private fun deliver(
      kind: NotificationKind = NotificationKind.NEW_APPLICATION,
      eventId: String = "event-1",
      senderId: String = "sender-1",
      data: Map<String, String> = emptyMap(),
  ) = inbox.deliver(listOf(user), kind, eventId, "Event", senderId, data).single()

  /**
   * A format that makes one notification per event, its text listing the ids of its items, oldest
   * first, or the user's name when there is one. It makes nothing of an item of the event
   * [UNREADABLE], as of one it cannot read.
   */
  private class GroupingFormat(override val kind: NotificationKind) : NotificationFormat {
    val calls = mutableListOf<List<InboxNotification>>()

    override fun format(
        items: List<InboxNotification>,
        texts: NotificationTexts,
    ): List<AppNotification> {
      calls += items
      return items
          .filter { it.eventId != UNREADABLE }
          .groupBy { it.eventId }
          .map { (eventId, eventItems) ->
            AppNotification(
                kind,
                eventId,
                "Event",
                eventItems.joinToString(",") { it.data[NAME_KEY.getValue(kind)] ?: it.id },
                eventItems.last().createdAt,
                NotificationTarget.Requests(eventId, "Event"),
            )
          }
    }
  }

  /**
   * A format that makes, of its items, one notification per event and one per sender, each listing
   * its items' ids.
   */
  private class EventAndSenderFormat(override val kind: NotificationKind) : NotificationFormat {
    override fun format(
        items: List<InboxNotification>,
        texts: NotificationTexts,
    ): List<AppNotification> {
      val byEvent = items.groupBy { "event:${it.eventId}" }
      val bySender = items.groupBy { "sender:${it.senderId}" }
      return (byEvent + bySender).map { (key, keyItems) ->
        AppNotification(
            kind,
            key,
            "Event",
            keyItems.joinToString(",") { it.id },
            keyItems.last().createdAt,
            NotificationTarget.Requests("event-1", "Event"),
        )
      }
    }
  }

  /** A check that shows [shown] each time it runs, or throws [failure]. */
  private class StubCheck(
      override val kind: NotificationKind,
      private val shown: List<AppNotification> = emptyList(),
      private val failure: Exception? = null,
  ) : TimedCheck {
    var runs = 0

    override suspend fun run(userId: String, now: Instant, seen: SeenStore): List<AppNotification> {
      runs++
      failure?.let { throw it }
      return shown
    }
  }

  private fun openSpots(key: String) =
      AppNotification(
          NotificationKind.OPEN_SPOTS,
          key,
          "Event",
          "Short",
          now,
          NotificationTarget.Mission("event-1", "Event", UserMode.ORGANIZER, key),
      )

  @Test
  fun run_givesEachKindToItsFormat_showsThem_andEmptiesTheInbox() = runTest {
    val application = deliver(NotificationKind.NEW_APPLICATION)
    val announcement = deliver(NotificationKind.ANNOUNCEMENT, senderId = "sender-2")

    engine().run(user, now)

    assertEquals(
        listOf(listOf(application.id)),
        applications.calls.map { c -> c.map { it.id } }.distinct(),
    )
    assertEquals(
        listOf(listOf(announcement.id)),
        announcements.calls.map { c -> c.map { it.id } }.distinct(),
    )
    assertEquals(
        setOf("NEW_APPLICATION:event-1", "ANNOUNCEMENT:event-1"),
        notifier.shown.map { it.identity }.toSet(),
    )
    assertTrue(inbox.pending(user).isEmpty())
  }

  @Test
  fun run_withANewItemForANotificationStillShown_groupsThemInOne() = runTest {
    val first = deliver()
    engine().run(user, now)
    deliveredAt = now + Duration.ofMinutes(1)
    val second = deliver(senderId = "sender-2")

    engine().run(user, now + Duration.ofMinutes(1))

    assertEquals(listOf(first.id, second.id), applications.calls.last().map { it.id })
    assertEquals("Alice,${second.id}", notifier.active.getValue("NEW_APPLICATION:event-1").text)
  }

  @Test
  fun run_afterTheNotificationWasDismissed_startsANewOne() = runTest {
    deliver()
    engine().run(user, now)
    notifier.dismiss("NEW_APPLICATION:event-1")
    val second = deliver(senderId = "sender-2")

    engine().run(user, now)

    assertEquals(second.id, notifier.active.getValue("NEW_APPLICATION:event-1").text)
  }

  @Test
  fun run_showsAgainOnlyTheNotificationsANewItemJoins() = runTest {
    deliver(eventId = "event-1")
    engine().run(user, now)
    deliver(eventId = "event-2")

    engine().run(user, now)

    assertEquals(
        listOf("NEW_APPLICATION:event-1", "NEW_APPLICATION:event-2"),
        notifier.shown.map { it.identity },
    )
  }

  @Test
  fun handleInbox_afterTheRemovalOfAShownItemFailed_doesNotCountItTwice() = runTest {
    deliver()
    var removalFails = true
    val flakyInbox =
        object : NotificationInbox by inbox {
          override suspend fun remove(userId: String, ids: Collection<String>) {
            if (removalFails) throw IOException("disk full")
            inbox.remove(userId, ids)
          }
        }
    val engine = engine(inbox = flakyInbox)
    runCatching { engine.handleInbox(user, now) }
    removalFails = false

    engine.handleInbox(user, now)

    assertEquals(listOf("Alice", "Alice"), notifier.shown.map { it.text })
    assertTrue(inbox.pending(user).isEmpty())
  }

  @Test
  fun run_withAKindTurnedOff_removesItWithoutShowingIt() = runTest {
    preferences.disable(user, NotificationKind.NEW_APPLICATION)
    deliver(NotificationKind.NEW_APPLICATION)
    deliver(NotificationKind.ANNOUNCEMENT)

    engine().run(user, now)

    assertEquals(listOf("ANNOUNCEMENT:event-1"), notifier.shown.map { it.identity })
    assertTrue(applications.calls.isEmpty())
    assertTrue(inbox.pending(user).isEmpty())
  }

  @Test
  fun run_removesWithoutShowingWhatIsOlderThan48Hours() = runTest {
    deliveredAt = now - MAX_INBOX_AGE - Duration.ofSeconds(1)
    deliver(eventId = "too-old")
    deliveredAt = now - MAX_INBOX_AGE
    deliver(eventId = "just-in-time")

    engine().run(user, now)

    assertEquals(listOf("NEW_APPLICATION:just-in-time"), notifier.shown.map { it.identity })
    assertTrue(inbox.pending(user).isEmpty())
  }

  @Test
  fun run_namesTheSenderFromTheirProfile_notFromTheData() = runTest {
    deliver(data = mapOf(NotificationData.Key.USER_NAME to "Mallory"))
    deliver(
        NotificationKind.ANNOUNCEMENT,
        data = mapOf(NotificationData.Key.SENDER_NAME to "Mallory"),
    )

    engine().run(user, now)

    assertEquals(listOf("Alice", "Alice"), notifier.shown.map { it.text })
  }

  @Test
  fun run_keepsTheNameOfTheData_whenTheProfileHasNoNameOrCannotBeRead() = runTest {
    deliver(senderId = "no-profile", data = mapOf(NotificationData.Key.USER_NAME to "Bob"))
    engine().run(user, now)
    users.failure = UserRepositoryException.Unavailable()
    deliver(eventId = "event-2", data = mapOf(NotificationData.Key.USER_NAME to "Carol"))

    engine().run(user, now)

    assertEquals(listOf("Bob", "Carol"), notifier.shown.map { it.text })
  }

  @Test
  fun run_leavesInTheInboxAKindWithoutAFormat() = runTest {
    val decision = deliver(NotificationKind.APPLICATION_DECIDED)

    engine().run(user, now)

    assertTrue(notifier.shown.isEmpty())
    assertEquals(listOf(decision), inbox.pending(user))
  }

  @Test
  fun run_removesWithoutShowingAnItemItsFormatCannotRead() = runTest {
    deliver(eventId = UNREADABLE)

    engine().run(user, now)

    assertTrue(notifier.shown.isEmpty())
    assertTrue(inbox.pending(user).isEmpty())
  }

  @Test
  fun handleInbox_whenThePreferencesCannotBeRead_throwsAndRemovesNothing() = runTest {
    val item = deliver()
    preferences.failure = IOException("offline")

    val thrown = runCatching { engine().handleInbox(user, now) }.exceptionOrNull()

    assertTrue(thrown is IOException)
    assertTrue(notifier.shown.isEmpty())
    assertEquals(listOf(item), inbox.pending(user))
  }

  @Test
  fun run_whenThePreferencesNeverCome_givesUp_andLetsTheNextRunThrough() = runTest {
    // Offline, with a profile this phone never cached, the preferences emit nothing.
    var offline = true
    val silentWhileOffline = NotificationPreferences { userId ->
      if (offline) flow { awaitCancellation() } else preferences.disabledKinds(userId)
    }
    val item = deliver()
    val check = StubCheck(NotificationKind.OPEN_SPOTS, listOf(openSpots("mission-1")))
    val engine = engine(checks = listOf(check), preferences = silentWhileOffline)

    val thrown = runCatching { engine.run(user, now) }.exceptionOrNull()

    assertTrue(thrown is TimeoutException)
    assertTrue(notifier.shown.isEmpty())
    assertEquals(listOf(item), inbox.pending(user))
    offline = false
    engine.run(user, now)
    assertEquals(2, notifier.shown.size)
    assertTrue(inbox.pending(user).isEmpty())
  }

  @Test
  fun run_runsTheChecksOfTheKindsTurnedOn() = runTest {
    preferences.disable(user, NotificationKind.MISSED_CHECK_IN)
    val openSpots = StubCheck(NotificationKind.OPEN_SPOTS, listOf(openSpots("mission-1")))
    val missedCheckIn = StubCheck(NotificationKind.MISSED_CHECK_IN, listOf(openSpots("x")))

    engine(checks = listOf(openSpots, missedCheckIn)).run(user, now)

    assertEquals(listOf("OPEN_SPOTS:mission-1"), notifier.shown.map { it.identity })
    assertEquals(0, missedCheckIn.runs)
  }

  @Test
  fun runChecks_withAFailingCheck_runsTheOthers_thenThrowsTheFailure() = runTest {
    val failure = IOException("offline")
    val failing = StubCheck(NotificationKind.MISSED_CHECK_IN, failure = failure)
    val openSpots = StubCheck(NotificationKind.OPEN_SPOTS, listOf(openSpots("mission-1")))

    val thrown = runCatching {
      engine(checks = listOf(failing, openSpots)).runChecks(user, now)
    }
        .exceptionOrNull()

    assertSame(failure, thrown)
    assertEquals(listOf("OPEN_SPOTS:mission-1"), notifier.shown.map { it.identity })
  }

  @Test
  fun run_withAnItemArrivingAfterANewerOneWasShown_givesTheFormatThemOldestFirst() = runTest {
    // As for a notification written offline, whose time is the server's when it reaches it.
    deliveredAt = now
    val newer = deliver(senderId = "sender-2")
    engine().run(user, now)
    deliveredAt = now - Duration.ofMinutes(5)
    val older = deliver(senderId = "sender-2")

    engine().run(user, now)

    assertEquals(listOf(older.id, newer.id), applications.calls.last().map { it.id })
  }

  @Test
  fun run_doesNotShowAgainANotificationAnotherIdentityOfTheItemsMakes() = runTest {
    // Per event and per sender: a new item of event-1 must not show "sender-1" again with only
    // the items of event-1, which would hide its item of event-2.
    val format = EventAndSenderFormat(NotificationKind.NEW_APPLICATION)
    val engine = engine(formats = listOf(format))
    deliver(eventId = "event-1", senderId = "sender-2")
    deliver(eventId = "event-2", senderId = "sender-2")
    engine.run(user, now)
    deliver(eventId = "event-1", senderId = "sender-3")

    engine.run(user, now)

    assertEquals(1, notifier.shown.count { it.key == "sender:sender-2" })
    assertEquals(2, notifier.shown.count { it.key == "event:event-1" })
  }

  @Test
  fun run_formatsEachIdentityWithItsOwnItems() = runTest {
    // "event:event-2" was dismissed while "sender:sender-2" is still shown: the new item of both
    // starts "event:event-2" again, without the item of event-2 that "sender:sender-2" holds.
    val format = EventAndSenderFormat(NotificationKind.NEW_APPLICATION)
    val engine = engine(formats = listOf(format))
    deliver(eventId = "event-1", senderId = "sender-2")
    deliver(eventId = "event-2", senderId = "sender-2")
    engine.run(user, now)
    notifier.dismiss("NEW_APPLICATION:event:event-2")
    val new = deliver(eventId = "event-2", senderId = "sender-2")

    engine.run(user, now)

    assertEquals(new.id, notifier.active.getValue("NEW_APPLICATION:event:event-2").text)
    assertEquals(
        3,
        notifier.active.getValue("NEW_APPLICATION:sender:sender-2").text.split(",").size,
    )
  }

  @Test
  fun run_whileItCannotShow_removesOnlyWhatItWouldNeverShow() = runTest {
    notifier.permitted = false
    preferences.disable(user, NotificationKind.ANNOUNCEMENT)
    deliveredAt = now - Duration.ofHours(48) - Duration.ofSeconds(1)
    deliver(eventId = "too-old")
    deliveredAt = now
    deliver(NotificationKind.ANNOUNCEMENT)
    val item = deliver()
    val check = StubCheck(NotificationKind.OPEN_SPOTS, listOf(openSpots("mission-1")))

    engine(checks = listOf(check)).run(user, now)

    assertTrue(notifier.shown.isEmpty())
    assertEquals(listOf(item), inbox.pending(user))
    assertEquals(0, check.runs)
  }

  @Test
  fun handleInbox_whenShowingFails_throwsAndRemovesNothing() = runTest {
    val item = deliver()
    val failure = IllegalStateException("notifier down")
    notifier.failure = failure

    val thrown = runCatching { engine().handleInbox(user, now) }.exceptionOrNull()

    assertSame(failure, thrown)
    assertEquals(listOf(item), inbox.pending(user))
  }

  @Test
  fun run_whenTheInboxCannotBeRead_stillRunsTheChecks_thenThrows() = runTest {
    val failure = IOException("offline")
    inbox.failure = failure
    val check = StubCheck(NotificationKind.OPEN_SPOTS, listOf(openSpots("mission-1")))

    val thrown = runCatching { engine(checks = listOf(check)).run(user, now) }.exceptionOrNull()

    assertSame(failure, thrown)
    assertEquals(listOf("OPEN_SPOTS:mission-1"), notifier.shown.map { it.identity })
  }

  @Test
  fun run_whenAFormatFails_keepsItsKindInTheInbox_andHandlesTheOthers() = runTest {
    val failure = IllegalStateException("malformed data")
    val failing =
        object : NotificationFormat {
          override val kind = NotificationKind.NEW_APPLICATION

          override fun format(items: List<InboxNotification>, texts: NotificationTexts) =
              throw failure
        }
    val application = deliver(NotificationKind.NEW_APPLICATION)
    deliver(NotificationKind.ANNOUNCEMENT)
    val engine = engine(formats = listOf(failing, announcements))

    val thrown = runCatching { engine.run(user, now) }.exceptionOrNull()

    assertSame(failure, thrown)
    assertEquals(listOf("ANNOUNCEMENT:event-1"), notifier.shown.map { it.identity })
    assertEquals(listOf(application), inbox.pending(user))
    // Once too old, the item that fails leaves the inbox, so it never blocks it.
    engine.run(user, now + MAX_INBOX_AGE + Duration.ofSeconds(1))
    assertTrue(inbox.pending(user).isEmpty())
  }

  @Test
  fun run_whenAFormatFailsOnOneItem_showsTheOthersOfItsKind_andKeepsThatOne() = runTest {
    val failure = IllegalStateException("malformed data")
    val failingOnOne =
        object : NotificationFormat {
          override val kind = NotificationKind.NEW_APPLICATION

          override fun format(items: List<InboxNotification>, texts: NotificationTexts) =
              if (items.any { it.eventId == "malformed" }) throw failure
              else applications.format(items, texts)
        }
    val malformed = deliver(eventId = "malformed")
    deliver(eventId = "event-2")

    val thrown = runCatching {
      engine(formats = listOf(failingOnOne)).run(user, now)
    }
        .exceptionOrNull()

    assertSame(failure, thrown)
    assertEquals(listOf("NEW_APPLICATION:event-2"), notifier.shown.map { it.identity })
    assertEquals(listOf(malformed), inbox.pending(user))
  }

  @Test
  fun runChecks_whenShowingFails_keepsTheCheckNotificationForTheNextRun() = runTest {
    // Like OpenSpots: forgets the mission no longer short, shows and marks the one short.
    val check =
        object : TimedCheck {
          override val kind = NotificationKind.OPEN_SPOTS

          override suspend fun run(userId: String, now: Instant, seen: SeenStore) =
              if (seen.wasShown("mission-1")) emptyList()
              else {
                seen.forget("mission-0")
                seen.markShown("mission-1")
                listOf(openSpots("mission-1"))
              }
        }
    val seen = InMemorySeenStore().apply { markShown("mission-0") }
    seenStores[NotificationKind.OPEN_SPOTS] = seen
    val engine = engine(checks = listOf(check))
    notifier.failure = IllegalStateException("notifier down")

    runCatching { engine.runChecks(user, now) }

    assertFalse(seen.wasShown("mission-1"))
    assertFalse(seen.wasShown("mission-0"))
    notifier.failure = null
    engine.runChecks(user, now)
    assertEquals(listOf("OPEN_SPOTS:mission-1"), notifier.shown.map { it.identity })
    assertTrue(seen.wasShown("mission-1"))
  }

  @Test
  fun runChecks_whenACheckFailsAfterForgettingAKey_keepsTheForgetOnly() = runTest {
    val failure = IOException("missions offline")
    val check =
        object : TimedCheck {
          override val kind = NotificationKind.OPEN_SPOTS

          override suspend fun run(
              userId: String,
              now: Instant,
              seen: SeenStore,
          ): List<AppNotification> {
            seen.forget("mission-0")
            seen.markShown("mission-1")
            throw failure
          }
        }
    val seen = InMemorySeenStore().apply { markShown("mission-0") }
    seenStores[NotificationKind.OPEN_SPOTS] = seen

    val thrown = runCatching {
      engine(checks = listOf(check)).runChecks(user, now)
    }
        .exceptionOrNull()

    assertSame(failure, thrown)
    assertFalse(seen.wasShown("mission-0"))
    assertFalse(seen.wasShown("mission-1"))
  }

  @Test
  fun run_whenTheInboxAndACheckFail_throwsTheInboxFailure() = runTest {
    val failure = IOException("inbox offline")
    inbox.failure = failure
    val check = StubCheck(NotificationKind.OPEN_SPOTS, failure = IOException("check offline"))

    val thrown = runCatching { engine(checks = listOf(check)).run(user, now) }.exceptionOrNull()

    assertSame(failure, thrown)
  }

  @Test
  fun runChecks_withTwoFailingChecks_throwsTheFirstFailure() = runTest {
    val first = IOException("first")
    val checks =
        listOf(
            StubCheck(NotificationKind.MISSED_CHECK_IN, failure = first),
            StubCheck(NotificationKind.OPEN_SPOTS, failure = IOException("second")),
        )

    val thrown = runCatching { engine(checks = checks).runChecks(user, now) }.exceptionOrNull()

    assertSame(first, thrown)
  }

  @Test
  fun runChecks_whenCancelled_stopsAtOnce() = runTest {
    val cancelled = StubCheck(NotificationKind.MISSED_CHECK_IN, failure = CancellationException())
    val next = StubCheck(NotificationKind.OPEN_SPOTS, listOf(openSpots("mission-1")))

    val thrown = runCatching {
      engine(checks = listOf(cancelled, next)).runChecks(user, now)
    }
        .exceptionOrNull()

    assertTrue(thrown is CancellationException)
    assertEquals(0, next.runs)
  }

  @Test
  fun runs_calledAtOnce_neverOverlap() = runTest {
    val released = CompletableDeferred<Unit>()
    var reading = 0
    var mostAtOnce = 0
    val slowPreferences = NotificationPreferences {
      flow {
        reading++
        mostAtOnce = maxOf(mostAtOnce, reading)
        released.await()
        reading--
        emit(emptySet())
      }
    }
    val engine = engine(preferences = slowPreferences)
    deliver()

    launch { engine.run(user, now) }
    launch { engine.handleInbox(user, now) }
    launch { engine.runChecks(user, now) }
    runCurrent()
    released.complete(Unit)
    advanceUntilIdle()

    assertEquals(1, mostAtOnce)
    // The second run read the inbox once the first had emptied it.
    assertEquals(1, notifier.shown.size)
  }

  @Test
  fun engine_refusesTwoFormatsOfOneKind_aFormatOfAComputedKind_andACheckOfAnInboxKind() {
    assertThrows(IllegalArgumentException::class.java) {
      engine(formats = listOf(applications, GroupingFormat(NotificationKind.NEW_APPLICATION)))
    }
    assertThrows(IllegalArgumentException::class.java) {
      engine(formats = listOf(GroupingFormat(NotificationKind.OPEN_SPOTS)))
    }
    assertThrows(IllegalArgumentException::class.java) {
      engine(checks = listOf(StubCheck(NotificationKind.ANNOUNCEMENT)))
    }
  }

  private companion object {
    /** The event whose items [GroupingFormat] cannot read. */
    const val UNREADABLE = "unreadable"

    /** Where [GroupingFormat] reads the user's name, per kind. */
    val NAME_KEY =
        mapOf(
            NotificationKind.NEW_APPLICATION to NotificationData.Key.USER_NAME,
            NotificationKind.ANNOUNCEMENT to NotificationData.Key.SENDER_NAME,
        )
  }
}
